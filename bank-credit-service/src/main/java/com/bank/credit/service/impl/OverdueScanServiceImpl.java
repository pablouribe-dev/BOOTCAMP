package com.bank.credit.service.impl;

import com.bank.credit.adapter.ReactorRxAdapter;
import com.bank.credit.dto.OverdueScanResponse;
import com.bank.credit.event.CreditDebtEventPublisher;
import com.bank.credit.model.Credit;
import com.bank.credit.repository.CreditRepository;
import com.bank.credit.service.OverdueScanService;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de la deteccion de deudas vencidas.
 *
 * <p>Primero publica y despues marca {@code overdueNotified = true}: si Kafka
 * falla, el credito queda sin marcar y la siguiente corrida lo vuelve a
 * intentar.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueScanServiceImpl implements OverdueScanService {

    private final CreditRepository creditRepository;
    private final CreditDebtEventPublisher creditDebtEventPublisher;
    private final Clock clock;

    /**
     * Indica si un credito tiene deuda vencida a la fecha dada.
     *
     * @param credit credito
     * @param today  fecha de referencia
     * @return {@code true} si {@code dueAmount > 0} y el vencimiento ya paso
     */
    public static boolean isOverdue(Credit credit, LocalDate today) {
        return credit.getDueAmount() != null && credit.getDueAmount().signum() > 0
                && credit.getDueDate() != null && credit.getDueDate().isBefore(today);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<OverdueScanResponse> scan() {
        LocalDate today = LocalDate.now(clock);
        log.info("Scanning credits for overdue debts (due before {})", today);
        return ReactorRxAdapter.fluxToFlowable(creditRepository.findByDueDateBeforeAndOverdueNotifiedNot(today, true))
                .filter(credit -> isOverdue(credit, today))
                .concatMapSingle(credit -> creditDebtEventPublisher.publishDetected(credit)
                        .andThen(Single.defer(() -> {
                            credit.setOverdueNotified(true);
                            return ReactorRxAdapter.monoToSingle(creditRepository.save(credit));
                        }))
                        .doOnSuccess(saved -> log.warn("Credit {} of customer {} is overdue: {} due on {}",
                                saved.getId(), saved.getCustomerId(), saved.getDueAmount(), saved.getDueDate())))
                .count()
                .map(count -> OverdueScanResponse.builder()
                        .productType(CreditDebtEventPublisher.PRODUCT_TYPE)
                        .detected(count)
                        .build())
                .doOnSuccess(response -> log.info("Overdue scan finished: {} new overdue credit(s)",
                        response.getDetected()));
    }
}
