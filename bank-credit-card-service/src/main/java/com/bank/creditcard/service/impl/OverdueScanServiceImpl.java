package com.bank.creditcard.service.impl;

import com.bank.creditcard.adapter.ReactorRxAdapter;
import com.bank.creditcard.dto.OverdueScanResponse;
import com.bank.creditcard.event.CardDebtEventPublisher;
import com.bank.creditcard.model.CreditCard;
import com.bank.creditcard.repository.CreditCardRepository;
import com.bank.creditcard.service.OverdueScanService;
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
 * falla, la tarjeta queda sin marcar y la siguiente corrida lo vuelve a
 * intentar.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueScanServiceImpl implements OverdueScanService {

    private final CreditCardRepository creditCardRepository;
    private final CardDebtEventPublisher cardDebtEventPublisher;
    private final Clock clock;

    /**
     * Indica si una tarjeta tiene deuda vencida a la fecha dada.
     *
     * @param card tarjeta de credito
     * @param today  fecha de referencia
     * @return {@code true} si {@code dueAmount > 0} y el vencimiento ya paso
     */
    public static boolean isOverdue(CreditCard card, LocalDate today) {
        return card.getDueAmount() != null && card.getDueAmount().signum() > 0
                && card.getPaymentDueDate() != null && card.getPaymentDueDate().isBefore(today);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<OverdueScanResponse> scan() {
        LocalDate today = LocalDate.now(clock);
        log.info("Scanning credit cards for overdue debts (due before {})", today);
        return ReactorRxAdapter.fluxToFlowable(
                        creditCardRepository.findByPaymentDueDateBeforeAndOverdueNotifiedNot(today, true))
                .filter(card -> isOverdue(card, today))
                .concatMapSingle(card -> cardDebtEventPublisher.publishDetected(card)
                        .andThen(Single.defer(() -> {
                            card.setOverdueNotified(true);
                            return ReactorRxAdapter.monoToSingle(creditCardRepository.save(card));
                        }))
                        .doOnSuccess(saved -> log.warn("Credit card {} of customer {} is overdue: {} due on {}",
                                saved.getId(), saved.getCustomerId(), saved.getDueAmount(), saved.getPaymentDueDate())))
                .count()
                .map(count -> OverdueScanResponse.builder()
                        .productType(CardDebtEventPublisher.PRODUCT_TYPE)
                        .detected(count)
                        .build())
                .doOnSuccess(response -> log.info("Overdue scan finished: {} new overdue credit card(s)",
                        response.getDetected()));
    }
}
