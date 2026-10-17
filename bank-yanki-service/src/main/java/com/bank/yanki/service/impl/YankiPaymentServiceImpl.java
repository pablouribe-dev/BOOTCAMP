package com.bank.yanki.service.impl;

import com.bank.yanki.adapter.ReactorRxAdapter;
import com.bank.yanki.config.YankiProperties;
import com.bank.yanki.dto.YankiPaymentRequest;
import com.bank.yanki.dto.YankiPaymentResponse;
import com.bank.yanki.event.AccountOperationResultPayload;
import com.bank.yanki.event.YankiEventPublisher;
import com.bank.yanki.exception.BusinessRuleException;
import com.bank.yanki.exception.ResourceNotFoundException;
import com.bank.yanki.exception.ServiceUnavailableException;
import com.bank.yanki.mapper.YankiMapper;
import com.bank.yanki.model.DebitCardReplica;
import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.model.PaymentStep;
import com.bank.yanki.model.Wallet;
import com.bank.yanki.model.YankiPayment;
import com.bank.yanki.repository.DebitCardReplicaRepository;
import com.bank.yanki.repository.WalletRepository;
import com.bank.yanki.repository.YankiPaymentRepository;
import com.bank.yanki.service.YankiPaymentService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de la saga de pagos Yanki.
 *
 * <ol>
 *   <li>Debito del emisor: con tarjeta, {@code WITHDRAWAL} sobre su cuenta
 *       principal y se espera el resultado; sin tarjeta, se descuenta su saldo.</li>
 *   <li>Credito del receptor: con tarjeta, {@code DEPOSIT} sobre su cuenta
 *       principal; sin tarjeta, se suma a su saldo.</li>
 *   <li>Si el credito falla, se devuelve el debito al emisor y el pago queda
 *       {@code FAILED}.</li>
 *   <li>El resultado final se publica en {@code bank.yanki.events}.</li>
 * </ol>
 *
 * <p>Solo se usa la cuenta principal de la tarjeta. No hay transaccion
 * distribuida: la consistencia se logra con compensaciones.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YankiPaymentServiceImpl implements YankiPaymentService {

    /** Motivo: no se pudo publicar en Kafka. */
    static final String KAFKA_UNAVAILABLE = "KAFKA_UNAVAILABLE";

    /** Motivo: el monedero receptor ya no existe. */
    static final String RECEIVER_NOT_FOUND = "RECEIVER_NOT_FOUND";

    /** Motivo: la tarjeta del receptor no esta activa. */
    static final String RECEIVER_CARD_NOT_ACTIVE = "RECEIVER_CARD_NOT_ACTIVE";

    /** Motivo: el pago supero el tiempo maximo en PENDING. */
    static final String TIMEOUT = "TIMEOUT";

    private static final String ACTIVE = "ACTIVE";

    private final YankiPaymentRepository yankiPaymentRepository;
    private final WalletRepository walletRepository;
    private final DebitCardReplicaRepository debitCardReplicaRepository;
    private final YankiEventPublisher yankiEventPublisher;
    private final YankiMapper yankiMapper;
    private final YankiProperties yankiProperties;
    private final Clock clock;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<YankiPaymentResponse> send(YankiPaymentRequest request) {
        log.info("Yanki payment of {} from {} to {}", request.getAmount(), request.getFromPhoneNumber(),
                request.getToPhoneNumber());
        if (request.getFromPhoneNumber().equals(request.getToPhoneNumber())) {
            return Single.error(new BusinessRuleException("fromPhoneNumber and toPhoneNumber must be different"));
        }
        return walletByPhone(request.getFromPhoneNumber())
                .flatMap(sender -> walletByPhone(request.getToPhoneNumber()).map(receiver -> sender))
                .flatMap(sender -> linkedCard(sender).flatMap(card -> card.isPresent()
                        ? startCardDebit(request, card.get())
                        : debitWallet(request, sender)))
                .map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<YankiPaymentResponse> findById(String id) {
        return ReactorRxAdapter.monoToMaybe(yankiPaymentRepository.findById(id))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("Yanki payment not found with id "
                        + id)))
                .map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<YankiPaymentResponse> findAll(String phoneNumber) {
        return ReactorRxAdapter.fluxToFlowable(phoneNumber == null || phoneNumber.isBlank()
                        ? yankiPaymentRepository.findAllByOrderByCreatedAtDesc()
                        : yankiPaymentRepository.findByFromPhoneNumberOrToPhoneNumberOrderByCreatedAtDesc(phoneNumber,
                                phoneNumber))
                .map(yankiMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable handleOperationResult(String eventType, AccountOperationResultPayload result) {
        String operationId = result.getOperationId();
        boolean completed = AccountOperationResultPayload.ACCOUNT_OPERATION_COMPLETED.equals(eventType);
        return ReactorRxAdapter.monoToMaybe(yankiPaymentRepository
                        .findFirstByDebitOperationIdOrCreditOperationIdOrCompensationOperationId(operationId,
                                operationId, operationId))
                .flatMapCompletable(payment -> {
                    if (operationId.equals(payment.getDebitOperationId())) {
                        return onDebitResult(payment, completed, result.getReason());
                    }
                    if (operationId.equals(payment.getCreditOperationId())) {
                        return onCreditResult(payment, completed, result.getReason());
                    }
                    return onCompensationResult(payment, completed);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<Long> expireStalePayments() {
        LocalDateTime limit = LocalDateTime.now(clock).minus(yankiProperties.getPaymentTimeout());
        return ReactorRxAdapter.fluxToFlowable(yankiPaymentRepository.findByStatusAndCreatedAtBefore(
                        PaymentStatus.PENDING, limit))
                .concatMapSingle(payment -> {
                    log.warn("Yanki payment {} is still PENDING at step {} after {}: expiring it", payment.getId(),
                            payment.getStep(), yankiProperties.getPaymentTimeout());
                    Completable action = switch (payment.getStep()) {
                        case CREDIT_REQUESTED -> compensate(payment, TIMEOUT);
                        case COMPENSATING -> {
                            log.error("Compensation of Yanki payment {} did not answer: manual review required",
                                    payment.getId());
                            yield finishFailed(payment, payment.getFailureReason() + "/COMPENSATION_TIMEOUT");
                        }
                        default -> finishFailed(payment, TIMEOUT);
                    };
                    return action.toSingleDefault(1);
                })
                .count();
    }

    /**
     * Emisor con tarjeta: crea el pago y pide el retiro a su cuenta principal.
     *
     * @param request pago pedido
     * @param card    tarjeta del emisor
     * @return el pago pendiente
     */
    private Single<YankiPayment> startCardDebit(YankiPaymentRequest request, DebitCardReplica card) {
        YankiPayment payment = newPayment(request);
        payment.setStep(PaymentStep.DEBIT_REQUESTED);
        payment.setSenderAccountId(card.getMainAccountId());
        payment.setDebitOperationId(UUID.randomUUID().toString());
        return save(payment).flatMap(saved -> yankiEventPublisher.requestOperation(saved.getDebitOperationId(),
                        saved.getSenderAccountId(), YankiEventPublisher.WITHDRAWAL, saved.getAmount(), saved.getId())
                .andThen(Single.just(saved))
                .onErrorResumeNext(error -> finishFailed(saved, KAFKA_UNAVAILABLE)
                        .andThen(Single.error(new ServiceUnavailableException(
                                "The payment could not be sent to the account service; try again later")))));
    }

    /**
     * Emisor sin tarjeta: descuenta su saldo propio y sigue con el credito.
     *
     * @param request pago pedido
     * @param sender  monedero emisor
     * @return el pago (completado o pendiente del credito)
     */
    private Single<YankiPayment> debitWallet(YankiPaymentRequest request, Wallet sender) {
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Wallet {} has {} and cannot send {}", sender.getPhoneNumber(), sender.getBalance(),
                    request.getAmount());
            return Single.error(new BusinessRuleException("Insufficient wallet balance"));
        }
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        YankiPayment payment = newPayment(request);
        payment.setDebitCompleted(true);
        return ReactorRxAdapter.monoToSingle(walletRepository.save(sender))
                .flatMap(saved -> save(payment))
                .flatMap(this::creditReceiver);
    }

    /**
     * Debito hecho: acredita al receptor (por Kafka si tiene tarjeta, o en su
     * saldo propio). Si no se puede, compensa.
     *
     * @param payment pago con el debito hecho
     * @return el pago actualizado
     */
    private Single<YankiPayment> creditReceiver(YankiPayment payment) {
        return ReactorRxAdapter.monoToMaybe(walletRepository.findByPhoneNumber(payment.getToPhoneNumber()))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(receiver -> {
                    if (receiver.isEmpty()) {
                        return compensate(payment, RECEIVER_NOT_FOUND).andThen(Single.just(payment));
                    }
                    return linkedCard(receiver.get())
                            .flatMap(card -> card.isPresent()
                                    ? startCardCredit(payment, card.get())
                                    : creditWallet(payment, receiver.get()))
                            .onErrorResumeNext(error -> error instanceof BusinessRuleException
                                    ? compensate(payment, RECEIVER_CARD_NOT_ACTIVE).andThen(Single.just(payment))
                                    : Single.error(error));
                });
    }

    /**
     * Receptor con tarjeta: pide el deposito a su cuenta principal.
     *
     * @param payment pago
     * @param card    tarjeta del receptor
     * @return el pago pendiente del credito
     */
    private Single<YankiPayment> startCardCredit(YankiPayment payment, DebitCardReplica card) {
        payment.setStep(PaymentStep.CREDIT_REQUESTED);
        payment.setReceiverAccountId(card.getMainAccountId());
        payment.setCreditOperationId(UUID.randomUUID().toString());
        return save(payment).flatMap(saved -> yankiEventPublisher.requestOperation(saved.getCreditOperationId(),
                        saved.getReceiverAccountId(), YankiEventPublisher.DEPOSIT, saved.getAmount(), saved.getId())
                .andThen(Single.just(saved))
                .onErrorResumeNext(error -> compensate(saved, KAFKA_UNAVAILABLE).andThen(Single.just(saved))));
    }

    /**
     * Receptor sin tarjeta: suma a su saldo propio y completa el pago.
     *
     * @param payment  pago
     * @param receiver monedero receptor
     * @return el pago completado
     */
    private Single<YankiPayment> creditWallet(YankiPayment payment, Wallet receiver) {
        receiver.setBalance(receiver.getBalance().add(payment.getAmount()));
        return ReactorRxAdapter.monoToSingle(walletRepository.save(receiver))
                .flatMapCompletable(saved -> complete(payment))
                .andThen(Single.just(payment));
    }

    /**
     * Resultado del retiro al emisor.
     *
     * @param payment   pago
     * @param completed si la operacion se aplico
     * @param reason    motivo del rechazo
     * @return accion siguiente
     */
    private Completable onDebitResult(YankiPayment payment, boolean completed, String reason) {
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getStep() == PaymentStep.DEBIT_REQUESTED) {
            if (!completed) {
                return finishFailed(payment, reason);
            }
            payment.setDebitCompleted(true);
            return creditReceiver(payment).ignoreElement();
        }
        if (payment.getStatus() == PaymentStatus.FAILED && completed && !payment.isDebitCompleted()) {
            log.warn("Late debit of expired Yanki payment {}: refunding the sender", payment.getId());
            payment.setDebitCompleted(true);
            payment.setCompensationOperationId(UUID.randomUUID().toString());
            return save(payment).flatMapCompletable(saved -> requestRefund(saved));
        }
        return Completable.complete();
    }

    /**
     * Resultado del deposito al receptor.
     *
     * @param payment   pago
     * @param completed si la operacion se aplico
     * @param reason    motivo del rechazo
     * @return accion siguiente
     */
    private Completable onCreditResult(YankiPayment payment, boolean completed, String reason) {
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getStep() != PaymentStep.CREDIT_REQUESTED) {
            if (completed && payment.getStatus() == PaymentStatus.FAILED) {
                log.error("Late credit of Yanki payment {} after it failed: manual review required", payment.getId());
            }
            return Completable.complete();
        }
        return completed ? complete(payment) : compensate(payment, reason);
    }

    /**
     * Resultado de la devolucion al emisor.
     *
     * @param payment   pago
     * @param completed si la devolucion se aplico
     * @return accion siguiente
     */
    private Completable onCompensationResult(YankiPayment payment, boolean completed) {
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getStep() != PaymentStep.COMPENSATING) {
            return Completable.complete();
        }
        if (!completed) {
            log.error("Compensation of Yanki payment {} was rejected: manual review required", payment.getId());
            return finishFailed(payment, payment.getFailureReason() + "/COMPENSATION_REJECTED");
        }
        log.info("Yanki payment {} compensated: amount returned to {}", payment.getId(), payment.getFromPhoneNumber());
        return finishFailed(payment, payment.getFailureReason());
    }

    /**
     * Devuelve el debito al emisor (si se hizo) y deja el pago {@code FAILED}.
     * Con tarjeta la devolucion es un {@code DEPOSIT} asincrono; sin tarjeta se
     * suma al saldo propio.
     *
     * @param payment pago
     * @param reason  motivo del fallo
     * @return completable de la compensacion
     */
    private Completable compensate(YankiPayment payment, String reason) {
        payment.setFailureReason(reason);
        if (!payment.isDebitCompleted()) {
            return finishFailed(payment, reason);
        }
        log.warn("Compensating Yanki payment {} ({})", payment.getId(), reason);
        if (payment.getSenderAccountId() != null) {
            payment.setStep(PaymentStep.COMPENSATING);
            payment.setCompensationOperationId(UUID.randomUUID().toString());
            return save(payment).flatMapCompletable(saved -> requestRefund(saved)
                    .onErrorResumeNext(error -> {
                        log.error("Could not request the refund of Yanki payment {}: manual review required",
                                saved.getId());
                        return finishFailed(saved, reason + "/COMPENSATION_NOT_SENT");
                    }));
        }
        return ReactorRxAdapter.monoToMaybe(walletRepository.findByPhoneNumber(payment.getFromPhoneNumber()))
                .flatMapCompletable(sender -> {
                    sender.setBalance(sender.getBalance().add(payment.getAmount()));
                    return ReactorRxAdapter.monoToCompletable(walletRepository.save(sender));
                })
                .andThen(Completable.defer(() -> finishFailed(payment, reason)));
    }

    /**
     * Pide el deposito de devolucion a la cuenta principal del emisor.
     *
     * @param payment pago con {@code compensationOperationId}
     * @return completable de publicacion
     */
    private Completable requestRefund(YankiPayment payment) {
        return yankiEventPublisher.requestOperation(payment.getCompensationOperationId(), payment.getSenderAccountId(),
                YankiEventPublisher.DEPOSIT, payment.getAmount(), payment.getId());
    }

    /**
     * Pago completado: guarda y publica {@code YANKI_PAYMENT_COMPLETED}.
     *
     * @param payment pago
     * @return completable
     */
    private Completable complete(YankiPayment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setStep(PaymentStep.DONE);
        payment.setFailureReason(null);
        log.info("Yanki payment {} completed", payment.getId());
        return save(payment).flatMapCompletable(saved -> yankiEventPublisher.publishOutcome(saved).onErrorComplete());
    }

    /**
     * Pago fallido: guarda y publica {@code YANKI_PAYMENT_FAILED}.
     *
     * @param payment pago
     * @param reason  motivo
     * @return completable
     */
    private Completable finishFailed(YankiPayment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setStep(PaymentStep.DONE);
        payment.setFailureReason(reason);
        log.warn("Yanki payment {} failed: {}", payment.getId(), reason);
        return save(payment).flatMapCompletable(saved -> yankiEventPublisher.publishOutcome(saved).onErrorComplete());
    }

    /**
     * Tarjeta asociada al monedero, si tiene. Si la tarjeta asociada no existe
     * o no esta activa, falla con 409.
     *
     * @param wallet monedero
     * @return la tarjeta, o vacio si el monedero no tiene tarjeta
     */
    private Single<Optional<DebitCardReplica>> linkedCard(Wallet wallet) {
        if (wallet.getDebitCardNumber() == null) {
            return Single.just(Optional.empty());
        }
        return ReactorRxAdapter.monoToMaybe(debitCardReplicaRepository.findByCardNumber(wallet.getDebitCardNumber()))
                .filter(card -> ACTIVE.equals(card.getStatus()) && card.getMainAccountId() != null)
                .map(Optional::of)
                .switchIfEmpty(Single.error(() -> new BusinessRuleException("The debit card linked to wallet "
                        + wallet.getPhoneNumber() + " is not active")));
    }

    /**
     * Monedero por celular o 404.
     *
     * @param phoneNumber celular
     * @return el monedero
     */
    private Single<Wallet> walletByPhone(String phoneNumber) {
        return ReactorRxAdapter.monoToMaybe(walletRepository.findByPhoneNumber(phoneNumber))
                .switchIfEmpty(Single.error(() -> new ResourceNotFoundException("Wallet not found with phoneNumber "
                        + phoneNumber)));
    }

    /**
     * Pago nuevo en {@code PENDING}.
     *
     * @param request pago pedido
     * @return documento sin guardar
     */
    private YankiPayment newPayment(YankiPaymentRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        return YankiPayment.builder()
                .fromPhoneNumber(request.getFromPhoneNumber())
                .toPhoneNumber(request.getToPhoneNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Guarda el pago con la fecha de actualizacion.
     *
     * @param payment pago
     * @return el pago guardado
     */
    private Single<YankiPayment> save(YankiPayment payment) {
        payment.setUpdatedAt(LocalDateTime.now(clock));
        return ReactorRxAdapter.monoToSingle(yankiPaymentRepository.save(payment));
    }
}
