package com.bank.debitcard.service.impl;

import com.bank.debitcard.adapter.ReactorRxAdapter;
import com.bank.debitcard.dto.DebitPaymentRequest;
import com.bank.debitcard.dto.DebitPaymentResponse;
import com.bank.debitcard.event.AccountOperationResultPayload;
import com.bank.debitcard.event.DebitCardEventPublisher;
import com.bank.debitcard.exception.BusinessRuleException;
import com.bank.debitcard.exception.ResourceNotFoundException;
import com.bank.debitcard.exception.ServiceUnavailableException;
import com.bank.debitcard.mapper.DebitCardMapper;
import com.bank.debitcard.model.CardStatus;
import com.bank.debitcard.model.DebitCard;
import com.bank.debitcard.model.DebitPayment;
import com.bank.debitcard.model.PaymentStatus;
import com.bank.debitcard.repository.DebitCardRepository;
import com.bank.debitcard.repository.DebitPaymentRepository;
import com.bank.debitcard.service.DebitPaymentService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de los pagos con tarjeta de debito.
 *
 * <ol>
 *   <li>Pago en {@code PENDING} y retiro pedido a la cuenta principal.</li>
 *   <li>{@code ACCOUNT_OPERATION_COMPLETED}: el pago pasa a {@code COMPLETED}.</li>
 *   <li>{@code ACCOUNT_OPERATION_REJECTED}: se intenta la siguiente cuenta
 *       asociada, en orden; si no quedan, {@code FAILED}.</li>
 * </ol>
 *
 * <p>Los resultados se enlazan al pago por {@code currentOperationId}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebitPaymentServiceImpl implements DebitPaymentService {

    /** Motivo de fallo cuando no se pudo publicar la peticion. */
    static final String KAFKA_UNAVAILABLE = "KAFKA_UNAVAILABLE";

    private final DebitCardRepository debitCardRepository;
    private final DebitPaymentRepository debitPaymentRepository;
    private final DebitCardEventPublisher debitCardEventPublisher;
    private final DebitCardMapper debitCardMapper;

    /**
     * {@inheritDoc}
     *
     * <p>Si Kafka no acepta la peticion, el pago queda {@code FAILED} y se
     * responde 503.</p>
     */
    @Override
    public Single<DebitPaymentResponse> pay(String cardId, DebitPaymentRequest request) {
        log.info("Debit card {} pays {}", cardId, request.getAmount());
        return findCard(cardId)
                .flatMap(card -> {
                    if (card.getStatus() != CardStatus.ACTIVE || card.getMainAccountId() == null) {
                        return Single.error(new BusinessRuleException("Debit card " + cardId + " is not active"));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    DebitPayment payment = DebitPayment.builder()
                            .cardId(cardId)
                            .amount(request.getAmount())
                            .description(request.getDescription())
                            .status(PaymentStatus.PENDING)
                            .currentOperationId(UUID.randomUUID().toString())
                            .attemptedAccountIds(new ArrayList<>(List.of(card.getMainAccountId())))
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return ReactorRxAdapter.monoToSingle(debitPaymentRepository.save(payment))
                            .flatMap(saved -> requestCharge(saved, card.getMainAccountId())
                                    .andThen(Single.just(saved))
                                    .onErrorResumeNext(error -> markPublishFailure(saved)));
                })
                .map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<DebitPaymentResponse> findByCard(String cardId) {
        return findCard(cardId).flatMapPublisher(card -> ReactorRxAdapter.fluxToFlowable(
                        debitPaymentRepository.findByCardIdOrderByCreatedAtDesc(cardId)))
                .map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<DebitPaymentResponse> findById(String cardId, String paymentId) {
        return ReactorRxAdapter.monoToMaybe(debitPaymentRepository.findByIdAndCardId(paymentId, cardId))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException(
                        "Payment " + paymentId + " not found for debit card " + cardId)))
                .map(debitCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable handleOperationResult(String eventType, AccountOperationResultPayload result) {
        return ReactorRxAdapter.monoToMaybe(debitPaymentRepository.findByCurrentOperationId(result.getOperationId()))
                .filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .flatMapCompletable(payment -> AccountOperationResultPayload.ACCOUNT_OPERATION_COMPLETED
                        .equals(eventType)
                        ? complete(payment, result)
                        : tryNextAccount(payment, result.getReason()));
    }

    /**
     * Pago cobrado.
     *
     * @param payment pago pendiente
     * @param result  operacion completada
     * @return completable de guardado
     */
    private Completable complete(DebitPayment payment, AccountOperationResultPayload result) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setChargedAccountId(result.getAccountId());
        payment.setUpdatedAt(LocalDateTime.now());
        log.info("Debit payment {} completed on account {}", payment.getId(), result.getAccountId());
        return ReactorRxAdapter.monoToCompletable(debitPaymentRepository.save(payment));
    }

    /**
     * Rechazo: pide el retiro a la siguiente cuenta asociada que aun no se
     * intento (primero la principal, luego el resto en orden de asociacion).
     *
     * @param payment pago pendiente
     * @param reason  motivo del rechazo
     * @return completable de guardado y publicacion
     */
    private Completable tryNextAccount(DebitPayment payment, String reason) {
        return ReactorRxAdapter.monoToMaybe(debitCardRepository.findById(payment.getCardId()))
                .map(card -> nextAccount(card, payment.getAttemptedAccountIds()))
                .defaultIfEmpty(Optional.empty())
                .flatMapCompletable(next -> {
                    if (next.isEmpty()) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(reason);
                        payment.setUpdatedAt(LocalDateTime.now());
                        log.warn("Debit payment {} failed: no more linked accounts to try ({})", payment.getId(),
                                reason);
                        return ReactorRxAdapter.monoToCompletable(debitPaymentRepository.save(payment));
                    }
                    payment.setCurrentOperationId(UUID.randomUUID().toString());
                    payment.getAttemptedAccountIds().add(next.get());
                    payment.setFailureReason(reason);
                    payment.setUpdatedAt(LocalDateTime.now());
                    log.info("Debit payment {} rejected ({}): trying next account {}", payment.getId(), reason,
                            next.get());
                    return ReactorRxAdapter.monoToSingle(debitPaymentRepository.save(payment))
                            .flatMapCompletable(saved -> requestCharge(saved, next.get()));
                });
    }

    /**
     * Siguiente cuenta a intentar: la principal y luego las demas en orden,
     * salteando las ya intentadas.
     *
     * @param card      tarjeta
     * @param attempted cuentas ya intentadas
     * @return la siguiente cuenta, o vacio si no quedan
     */
    static Optional<String> nextAccount(DebitCard card, List<String> attempted) {
        return Stream.concat(Stream.ofNullable(card.getMainAccountId()), card.getAccountIds().stream())
                .filter(accountId -> !attempted.contains(accountId))
                .findFirst();
    }

    /**
     * Publica la peticion de retiro de la operacion en curso.
     *
     * @param payment   pago
     * @param accountId cuenta a cargar
     * @return completable de publicacion
     */
    private Completable requestCharge(DebitPayment payment, String accountId) {
        return debitCardEventPublisher.requestWithdrawal(payment.getCurrentOperationId(), accountId,
                payment.getAmount(), payment.getCardId());
    }

    /**
     * La peticion no se pudo publicar: el pago queda fallido y se responde 503.
     *
     * @param payment pago recien creado
     * @return error 503
     */
    private Single<DebitPayment> markPublishFailure(DebitPayment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(KAFKA_UNAVAILABLE);
        payment.setUpdatedAt(LocalDateTime.now());
        return ReactorRxAdapter.monoToSingle(debitPaymentRepository.save(payment))
                .flatMap(saved -> Single.error(new ServiceUnavailableException(
                        "The payment could not be sent to the account service; try again later")));
    }

    /**
     * Recupera una tarjeta o falla con 404.
     *
     * @param cardId identificador
     * @return la tarjeta
     */
    private Single<DebitCard> findCard(String cardId) {
        return ReactorRxAdapter.monoToMaybe(debitCardRepository.findById(cardId))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException(
                        "Debit card not found with id " + cardId)));
    }
}
