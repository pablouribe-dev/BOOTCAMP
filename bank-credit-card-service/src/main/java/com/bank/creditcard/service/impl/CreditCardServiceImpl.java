package com.bank.creditcard.service.impl;

import com.bank.creditcard.client.CustomerClient;
import com.bank.creditcard.client.CustomerDto;
import com.bank.creditcard.config.CardRulesProperties;
import com.bank.creditcard.dto.CardBalanceResponse;
import com.bank.creditcard.dto.CardOperationRequest;
import com.bank.creditcard.dto.CreditCardRequest;
import com.bank.creditcard.dto.CreditCardResponse;
import com.bank.creditcard.event.CardDebtEventPublisher;
import com.bank.creditcard.exception.BusinessRuleException;
import com.bank.creditcard.exception.ServiceUnavailableException;
import com.bank.creditcard.exception.ResourceNotFoundException;
import com.bank.creditcard.mapper.CreditCardMapper;
import com.bank.creditcard.model.CardOperationType;
import com.bank.creditcard.model.CardType;
import com.bank.creditcard.model.CreditCard;
import com.bank.creditcard.repository.CreditCardRepository;
import com.bank.creditcard.service.CreditCardService;
import com.bank.creditcard.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.bank.creditcard.adapter.ReactorRxAdapter;
import reactor.core.publisher.Flux;

/**
 * Implementacion de las reglas de negocio de las tarjetas de credito.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditCardServiceImpl implements CreditCardService {

    /** Longitud total del numero de tarjeta, incluido el prefijo. */
    private static final int CARD_NUMBER_LENGTH = 16;

    private static final Random RANDOM = new SecureRandom();

    private final CreditCardRepository creditCardRepository;
    private final CreditCardMapper creditCardMapper;
    private final CustomerClient customerClient;
    private final CardRulesProperties cardRules;
    private final OverdueDebtService overdueDebtService;
    private final CardDebtEventPublisher cardDebtEventPublisher;
    private final Clock clock;

    /**
     * {@inheritDoc}
     *
     * <p>No se exige que el cliente tenga una cuenta bancaria; solo que el tipo
     * de tarjeta coincida con su tipo de cliente.</p>
     *
     * <p>Parte III: si el cliente tiene deuda vencida (copia local
     * {@code overdue_debts}) se rechaza con 409 antes de consultar al cliente.</p>
     */
    @Override
    public Single<CreditCardResponse> create(CreditCardRequest request) {
        log.info("Issuing a {} credit card for customerId={}", request.getCardType(), request.getCustomerId());
        return overdueDebtService.ensureNoOverdueDebt(request.getCustomerId())
                .andThen(Single.defer(() -> customerClient.findById(request.getCustomerId())))
                .flatMapCompletable(customer -> validateIssue(customer, request))
                .andThen(Single.defer(() -> buildCard(request)))
                .flatMap(card -> ReactorRxAdapter.monoToSingle(creditCardRepository.save(card)))
                .map(creditCardMapper::toResponse)
                .doOnSuccess(response -> log.info("Credit card {} created with id={}",
                        response.getCardNumber(), response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<CreditCardResponse> findAll(String customerId) {
        boolean filtered = customerId != null && !customerId.isBlank();
        log.debug("Listing credit cards{}", filtered ? " for customerId=" + customerId : "");
        Flux<CreditCard> source = filtered
                ? creditCardRepository.findByCustomerId(customerId)
                : creditCardRepository.findAll();
        return ReactorRxAdapter.fluxToFlowable(source).map(creditCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CreditCardResponse> findById(String id) {
        log.debug("Looking for credit card with id={}", id);
        return findOrFail(id).map(creditCardMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     *
     * <p>El tipo de tarjeta y el cliente son inmutables. La linea de credito
     * puede cambiar siempre que no quede por debajo del importe consumido.</p>
     */
    @Override
    public Single<CreditCardResponse> update(String id, CreditCardRequest request) {
        log.info("Updating credit card with id={}", id);
        return findOrFail(id)
                .map(existing -> merge(existing, request))
                .flatMap(this::saveAndNotifySettlement)
                .map(creditCardMapper::toResponse)
                .doOnSuccess(response -> log.info("Credit card with id={} updated", response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting credit card with id={}", id);
        return findOrFail(id)
                .flatMapCompletable(card ->
                        ReactorRxAdapter.monoToCompletable(creditCardRepository.delete(card)))
                .doOnComplete(() -> log.info("Credit card with id={} deleted", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CardBalanceResponse> getBalance(String id) {
        log.debug("Checking available balance of credit card with id={}", id);
        return findOrFail(id).map(creditCardMapper::toBalanceResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CardBalanceResponse> applyBalanceOperation(String id, CardOperationRequest request) {
        log.info("Applying {} of {} to credit card with id={}", request.getOperation(), request.getAmount(), id);
        return findOrFail(id)
                .map(card -> applyOperation(card, request))
                .flatMap(this::saveAndNotifySettlement)
                .map(creditCardMapper::toBalanceResponse);
    }

    /**
     * Recupera una tarjeta o falla con 404 si no existe.
     *
     * @param id identificador de la tarjeta
     * @return el documento encontrado
     */
    private Single<CreditCard> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(creditCardRepository.findById(id))
                .switchIfEmpty(Single.error(() ->
                        new ResourceNotFoundException("Credit card not found with id " + id)));
    }

    /**
     * Valida que el tipo de tarjeta solicitado coincida con el tipo de cliente.
     *
     * @param customer cliente devuelto por {@code customer-service}
     * @param request  datos de la tarjeta
     * @return completable que falla con {@link BusinessRuleException} si los
     *         tipos no coinciden
     */
    private Completable validateIssue(CustomerDto customer, CreditCardRequest request) {
        if (customer.getType() == null) {
            return Completable.error(new ServiceUnavailableException(
                    "customer-service did not return the type of customer " + request.getCustomerId()));
        }
        CardType expected = switch (customer.getType()) {
            case PERSONAL -> CardType.PERSONAL;
            case BUSINESS -> CardType.BUSINESS;
        };
        if (request.getCardType() != expected) {
            log.warn("Customer {} is {} but requested a {} card",
                    request.getCustomerId(), customer.getType(), request.getCardType());
            return Completable.error(new BusinessRuleException(
                    "A " + customer.getType() + " customer can only hold " + expected + " credit cards"));
        }
        return Completable.complete();
    }

    /**
     * Completa el documento de tarjeta con numero generado y fecha de emision.
     *
     * @param request datos de la tarjeta
     * @return tarjeta lista para persistir
     */
    private Single<CreditCard> buildCard(CreditCardRequest request) {
        return generateCardNumber().map(cardNumber -> {
            CreditCard card = creditCardMapper.toDocument(request);
            card.setCardNumber(cardNumber);
            card.setCreatedAt(LocalDateTime.now());
            return card;
        });
    }

    /**
     * Genera un numero de tarjeta libre con el prefijo configurado.
     *
     * <p>Si el numero sorteado ya existe, vuelve a intentarlo.</p>
     *
     * @return numero de tarjeta de {@value #CARD_NUMBER_LENGTH} digitos
     */
    private Single<String> generateCardNumber() {
        return Single.defer(() -> {
            String prefix = cardRules.getCardNumberPrefix();
            int remaining = CARD_NUMBER_LENGTH - prefix.length();
            String candidate = prefix + RANDOM.ints(remaining, 0, 10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining());
            return ReactorRxAdapter.monoToSingle(creditCardRepository.existsByCardNumber(candidate))
                    .flatMap(exists -> Boolean.TRUE.equals(exists)
                            ? generateCardNumber()
                            : Single.just(candidate));
        });
    }

    /**
     * Guarda la tarjeta y, si estaba notificada como vencida y ya no lo esta
     * (se pago lo exigible o se movio la fecha limite), publica
     * {@code DEBT_OVERDUE_CLEARED} y baja la marca.
     *
     * @param card tarjeta modificada
     * @return tarjeta guardada
     */
    private Single<CreditCard> saveAndNotifySettlement(CreditCard card) {
        boolean settled = Boolean.TRUE.equals(card.getOverdueNotified())
                && !OverdueScanServiceImpl.isOverdue(card, LocalDate.now(clock));
        if (settled) {
            card.setOverdueNotified(false);
        }
        return ReactorRxAdapter.monoToSingle(creditCardRepository.save(card))
                .flatMap(saved -> settled
                        ? cardDebtEventPublisher.publishCleared(saved).onErrorComplete().andThen(Single.just(saved))
                        : Single.just(saved));
    }

    /**
     * Copia sobre la tarjeta existente los campos que admiten actualizacion.
     *
     * @param existing tarjeta almacenada
     * @param request  datos nuevos
     * @return la misma tarjeta con los valores actualizados
     * @throws BusinessRuleException si se intenta cambiar un campo inmutable o
     *                               reducir la linea por debajo del consumo
     */
    private CreditCard merge(CreditCard existing, CreditCardRequest request) {
        if (existing.getCardType() != request.getCardType()) {
            throw new BusinessRuleException("cardType cannot be modified");
        }
        if (!existing.getCustomerId().equals(request.getCustomerId())) {
            throw new BusinessRuleException("customerId cannot be modified");
        }
        if (request.getCreditLimit().compareTo(existing.getUsedAmount()) < 0) {
            log.warn("New creditLimit {} is below the used amount {} of card {}",
                    request.getCreditLimit(), existing.getUsedAmount(), existing.getId());
            throw new BusinessRuleException("creditLimit cannot be lower than the used amount");
        }
        existing.setCreditLimit(request.getCreditLimit());
        Optional.ofNullable(request.getPaymentDueDate()).ifPresent(existing::setPaymentDueDate);
        Optional.ofNullable(request.getDueAmount()).ifPresent(existing::setDueAmount);
        return existing;
    }

    /**
     * Aplica el consumo o el pago sobre el importe usado en memoria.
     *
     * @param card    tarjeta recuperada
     * @param request operacion e importe
     * @return la misma tarjeta con el importe usado actualizado
     * @throws BusinessRuleException si el consumo excede la linea disponible o
     *                               el pago supera el importe usado
     */
    private CreditCard applyOperation(CreditCard card, CardOperationRequest request) {
        BigDecimal amount = request.getAmount();
        if (request.getOperation() == CardOperationType.CHARGE) {
            BigDecimal available = creditCardMapper.availableBalance(card);
            if (available.compareTo(amount) < 0) {
                log.warn("Charge of {} exceeds the available balance {} of card {}",
                        amount, available, card.getCardNumber());
                throw new BusinessRuleException(
                        "The charge exceeds the available balance of card " + card.getCardNumber());
            }
            card.setUsedAmount(card.getUsedAmount().add(amount));
            log.info("Charged {} to card {}, used amount is now {}",
                    amount, card.getCardNumber(), card.getUsedAmount());
            return card;
        }
        if (card.getUsedAmount().compareTo(amount) < 0) {
            log.warn("Payment of {} exceeds the used amount {} of card {}",
                    amount, card.getUsedAmount(), card.getCardNumber());
            throw new BusinessRuleException(
                    "The payment exceeds the used amount of card " + card.getCardNumber());
        }
        card.setUsedAmount(card.getUsedAmount().subtract(amount));
        Optional.ofNullable(card.getDueAmount())
                .ifPresent(due -> card.setDueAmount(due.subtract(amount).max(BigDecimal.ZERO)));
        log.info("Paid {} of card {}, used amount is now {}",
                amount, card.getCardNumber(), card.getUsedAmount());
        return card;
    }
}
