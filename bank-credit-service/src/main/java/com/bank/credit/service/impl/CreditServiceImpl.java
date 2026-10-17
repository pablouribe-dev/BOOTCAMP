package com.bank.credit.service.impl;

import com.bank.credit.client.CustomerClient;
import com.bank.credit.client.CustomerDto;
import com.bank.credit.config.CreditRulesProperties;
import com.bank.credit.dto.CreditBalanceResponse;
import com.bank.credit.dto.CreditOperationRequest;
import com.bank.credit.dto.CreditRequest;
import com.bank.credit.dto.CreditResponse;
import com.bank.credit.event.CreditDebtEventPublisher;
import com.bank.credit.exception.BusinessRuleException;
import com.bank.credit.exception.ServiceUnavailableException;
import com.bank.credit.exception.ResourceNotFoundException;
import com.bank.credit.mapper.CreditMapper;
import com.bank.credit.model.Credit;
import com.bank.credit.model.CreditType;
import com.bank.credit.repository.CreditRepository;
import com.bank.credit.service.CreditService;
import com.bank.credit.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.bank.credit.adapter.ReactorRxAdapter;
import reactor.core.publisher.Flux;

/**
 * Implementacion de las reglas de negocio de los creditos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditServiceImpl implements CreditService {

    private final CreditRepository creditRepository;
    private final CreditMapper creditMapper;
    private final CustomerClient customerClient;
    private final CreditRulesProperties creditRules;
    private final OverdueDebtService overdueDebtService;
    private final CreditDebtEventPublisher creditDebtEventPublisher;
    private final Clock clock;

    /**
     * {@inheritDoc}
     *
     * <p>Un cliente personal solo puede tener la cantidad de creditos
     * configurada y todos de tipo {@code PERSONAL}; un cliente empresarial
     * puede tener varios, todos de tipo {@code BUSINESS}. No se exige que el
     * cliente tenga una cuenta bancaria.</p>
     *
     * <p>Parte III: si el cliente tiene deuda vencida (copia local
     * {@code overdue_debts}) se rechaza con 409 antes de consultar al cliente.</p>
     */
    @Override
    public Single<CreditResponse> create(CreditRequest request) {
        log.info("Granting a {} credit to customerId={}", request.getCreditType(), request.getCustomerId());
        return overdueDebtService.ensureNoOverdueDebt(request.getCustomerId())
                .andThen(Single.defer(() -> customerClient.findById(request.getCustomerId())))
                .flatMapCompletable(customer -> validateGrant(customer, request))
                .andThen(Single.defer(() -> ReactorRxAdapter.monoToSingle(
                        creditRepository.save(creditMapper.toDocument(request)))))
                .map(creditMapper::toResponse)
                .doOnSuccess(response -> log.info("Credit created with id={}", response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<CreditResponse> findAll(String customerId) {
        boolean filtered = customerId != null && !customerId.isBlank();
        log.debug("Listing credits{}", filtered ? " for customerId=" + customerId : "");
        Flux<Credit> source = filtered
                ? creditRepository.findByCustomerId(customerId)
                : creditRepository.findAll();
        return ReactorRxAdapter.fluxToFlowable(source).map(creditMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CreditResponse> findById(String id) {
        log.debug("Looking for credit with id={}", id);
        return findOrFail(id).map(creditMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     *
     * <p>El tipo de credito y el cliente son inmutables. El importe otorgado
     * solo puede corregirse mientras el credito no haya recibido pagos.</p>
     */
    @Override
    public Single<CreditResponse> update(String id, CreditRequest request) {
        log.info("Updating credit with id={}", id);
        return findOrFail(id)
                .map(existing -> merge(existing, request))
                .flatMap(this::saveAndNotifySettlement)
                .map(creditMapper::toResponse)
                .doOnSuccess(response -> log.info("Credit with id={} updated", response.getId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting credit with id={}", id);
        return findOrFail(id)
                .flatMapCompletable(credit ->
                        ReactorRxAdapter.monoToCompletable(creditRepository.delete(credit)))
                .doOnComplete(() -> log.info("Credit with id={} deleted", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CreditBalanceResponse> getBalance(String id) {
        log.debug("Checking outstanding balance of credit with id={}", id);
        return findOrFail(id).map(creditMapper::toBalanceResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<CreditBalanceResponse> applyBalanceOperation(String id, CreditOperationRequest request) {
        log.info("Applying {} of {} to credit with id={}", request.getOperation(), request.getAmount(), id);
        return findOrFail(id)
                .map(credit -> applyPayment(credit, request.getAmount()))
                .flatMap(this::saveAndNotifySettlement)
                .map(creditMapper::toBalanceResponse);
    }

    /**
     * Guarda el credito y, si estaba notificado como vencido y ya no lo esta
     * (se pago la cuota o se movio el vencimiento), publica
     * {@code DEBT_OVERDUE_CLEARED} y baja la marca.
     *
     * @param credit credito modificado
     * @return credito guardado
     */
    private Single<Credit> saveAndNotifySettlement(Credit credit) {
        boolean settled = Boolean.TRUE.equals(credit.getOverdueNotified())
                && !OverdueScanServiceImpl.isOverdue(credit, LocalDate.now(clock));
        if (settled) {
            credit.setOverdueNotified(false);
        }
        return ReactorRxAdapter.monoToSingle(creditRepository.save(credit))
                .flatMap(saved -> settled
                        ? creditDebtEventPublisher.publishCleared(saved).onErrorComplete().andThen(Single.just(saved))
                        : Single.just(saved));
    }

    /**
     * Recupera un credito o falla con 404 si no existe.
     *
     * @param id identificador del credito
     * @return el documento encontrado
     */
    private Single<Credit> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(creditRepository.findById(id))
                .switchIfEmpty(Single.error(() ->
                        new ResourceNotFoundException("Credit not found with id " + id)));
    }

    /**
     * Valida que el otorgamiento sea compatible con el tipo de cliente.
     *
     * @param customer cliente devuelto por {@code customer-service}
     * @param request  datos del credito
     * @return completable que falla con {@link BusinessRuleException} si alguna
     *         regla no se cumple
     */
    private Completable validateGrant(CustomerDto customer, CreditRequest request) {
        if (customer.getType() == null) {
            return Completable.error(new ServiceUnavailableException(
                    "customer-service did not return the type of customer " + request.getCustomerId()));
        }
        return switch (customer.getType()) {
            case PERSONAL -> validatePersonalGrant(request);
            case BUSINESS -> validateBusinessGrant(request);
        };
    }

    /**
     * Reglas de un cliente personal: credito de tipo {@code PERSONAL} y como
     * maximo la cantidad configurada en {@code bank.rules.max-personal-credits}.
     *
     * @param request datos del credito
     * @return completable que falla si el tipo no corresponde o ya alcanzo el
     *         maximo de creditos
     */
    private Completable validatePersonalGrant(CreditRequest request) {
        if (request.getCreditType() != CreditType.PERSONAL) {
            log.warn("Personal customer {} requested a {} credit",
                    request.getCustomerId(), request.getCreditType());
            return Completable.error(new BusinessRuleException(
                    "A personal customer can only hold PERSONAL credits"));
        }
        return ReactorRxAdapter.monoToSingle(creditRepository.countByCustomerId(request.getCustomerId()))
                .flatMapCompletable(count -> {
                    if (count >= creditRules.getMaxPersonalCredits()) {
                        log.warn("Personal customer {} already holds {} credit(s)",
                                request.getCustomerId(), count);
                        return Completable.error(new BusinessRuleException(
                                "A personal customer can only hold " + creditRules.getMaxPersonalCredits()
                                        + " credit(s)"));
                    }
                    return Completable.complete();
                });
    }

    /**
     * Reglas de un cliente empresarial: varios creditos, todos de tipo
     * {@code BUSINESS}.
     *
     * @param request datos del credito
     * @return completable que falla si el tipo no corresponde
     */
    private Completable validateBusinessGrant(CreditRequest request) {
        if (request.getCreditType() != CreditType.BUSINESS) {
            log.warn("Business customer {} requested a {} credit",
                    request.getCustomerId(), request.getCreditType());
            return Completable.error(new BusinessRuleException(
                    "A business customer can only hold BUSINESS credits"));
        }
        return Completable.complete();
    }

    /**
     * Copia sobre el credito existente los campos que admiten actualizacion.
     *
     * @param existing credito almacenado
     * @param request  datos nuevos
     * @return el mismo credito con los valores actualizados
     * @throws BusinessRuleException si se intenta cambiar un campo inmutable o
     *                               el importe otorgado de un credito con pagos
     */
    private Credit merge(Credit existing, CreditRequest request) {
        if (existing.getCreditType() != request.getCreditType()) {
            throw new BusinessRuleException("creditType cannot be modified");
        }
        if (!existing.getCustomerId().equals(request.getCustomerId())) {
            throw new BusinessRuleException("customerId cannot be modified");
        }
        existing.setInterestRate(request.getInterestRate());
        Optional.ofNullable(request.getDueDate()).ifPresent(existing::setDueDate);
        Optional.ofNullable(request.getDueAmount()).ifPresent(existing::setDueAmount);
        if (existing.getGrantedAmount().compareTo(request.getGrantedAmount()) != 0) {
            if (existing.getOutstandingBalance().compareTo(existing.getGrantedAmount()) != 0) {
                log.warn("Credit {} already received payments: grantedAmount is frozen", existing.getId());
                throw new BusinessRuleException(
                        "grantedAmount cannot be modified once the credit received payments");
            }
            existing.setGrantedAmount(request.getGrantedAmount());
            existing.setOutstandingBalance(request.getGrantedAmount());
        }
        return existing;
    }

    /**
     * Aplica un pago sobre el saldo pendiente en memoria.
     *
     * @param credit credito recuperado
     * @param amount importe del pago
     * @return el mismo credito con el saldo pendiente actualizado
     * @throws BusinessRuleException si el pago supera el saldo pendiente
     */
    private Credit applyPayment(Credit credit, BigDecimal amount) {
        if (credit.getOutstandingBalance().compareTo(amount) < 0) {
            log.warn("Payment of {} exceeds the outstanding balance {} of credit {}",
                    amount, credit.getOutstandingBalance(), credit.getId());
            throw new BusinessRuleException(
                    "The payment exceeds the outstanding balance of credit " + credit.getId());
        }
        credit.setOutstandingBalance(credit.getOutstandingBalance().subtract(amount));
        Optional.ofNullable(credit.getDueAmount())
                .ifPresent(due -> credit.setDueAmount(due.subtract(amount).max(BigDecimal.ZERO)));
        log.info("Paid {} of credit {}, outstanding balance is now {}",
                amount, credit.getId(), credit.getOutstandingBalance());
        return credit;
    }
}
