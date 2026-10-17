package com.bank.creditcard.service.impl;

import com.bank.creditcard.adapter.ReactorRxAdapter;
import com.bank.creditcard.event.DebtEventPayload;
import com.bank.creditcard.exception.BusinessRuleException;
import com.bank.creditcard.model.OverdueDebt;
import com.bank.creditcard.repository.OverdueDebtRepository;
import com.bank.creditcard.service.OverdueDebtService;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de la copia local de deudas vencidas.
 *
 * <p>Los eventos son snapshots: repetir un {@code DETECTED} actualiza el mismo
 * documento y repetir un {@code CLEARED} no borra nada nuevo, asi que
 * reprocesarlos es inofensivo.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueDebtServiceImpl implements OverdueDebtService {

    private final OverdueDebtRepository overdueDebtRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable registerOverdue(DebtEventPayload payload) {
        return ReactorRxAdapter.monoToMaybe(overdueDebtRepository.findByProductTypeAndProductId(
                        payload.getProductType(), payload.getProductId()))
                .defaultIfEmpty(OverdueDebt.builder().build())
                .flatMapCompletable(debt -> {
                    debt.setCustomerId(payload.getCustomerId());
                    debt.setProductType(payload.getProductType());
                    debt.setProductId(payload.getProductId());
                    debt.setDueDate(payload.getDueDate());
                    debt.setDueAmount(payload.getDueAmount());
                    debt.setUpdatedAt(Instant.now());
                    return ReactorRxAdapter.monoToCompletable(overdueDebtRepository.save(debt));
                })
                .doOnComplete(() -> log.info("Overdue debt registered: customer={} {} {}",
                        payload.getCustomerId(), payload.getProductType(), payload.getProductId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable clearOverdue(DebtEventPayload payload) {
        return ReactorRxAdapter.monoToCompletable(overdueDebtRepository.deleteByProductTypeAndProductId(
                        payload.getProductType(), payload.getProductId()))
                .doOnComplete(() -> log.info("Overdue debt cleared: customer={} {} {}",
                        payload.getCustomerId(), payload.getProductType(), payload.getProductId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable ensureNoOverdueDebt(String customerId) {
        return ReactorRxAdapter.monoToSingle(overdueDebtRepository.existsByCustomerId(customerId))
                .flatMapCompletable(hasDebt -> {
                    if (Boolean.TRUE.equals(hasDebt)) {
                        log.warn("Customer {} has overdue debt: new product rejected", customerId);
                        return Completable.error(new BusinessRuleException("Customer " + customerId
                                + " has an overdue debt and cannot acquire new products"));
                    }
                    return Completable.complete();
                });
    }
}
