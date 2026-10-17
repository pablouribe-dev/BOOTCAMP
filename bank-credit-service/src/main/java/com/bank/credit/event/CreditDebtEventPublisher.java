package com.bank.credit.event;

import com.bank.credit.config.BankKafkaProperties;
import com.bank.credit.model.Credit;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publica en {@code bank.debt.events} la deteccion y la liquidacion de deudas
 * vencidas de creditos, con el {@code customerId} como clave.
 */
@Component
@RequiredArgsConstructor
public class CreditDebtEventPublisher {

    /** Tipo de producto que viaja en el payload. */
    public static final String PRODUCT_TYPE = "CREDIT";

    private final EventPublisher eventPublisher;
    private final BankKafkaProperties kafkaProperties;

    /**
     * Publica {@code DEBT_OVERDUE_DETECTED}.
     *
     * @param credit credito con deuda vencida
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishDetected(Credit credit) {
        DebtEventPayload payload = DebtEventPayload.builder()
                .customerId(credit.getCustomerId())
                .productType(PRODUCT_TYPE)
                .productId(credit.getId())
                .dueDate(credit.getDueDate())
                .dueAmount(credit.getDueAmount())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("debt-events"), credit.getCustomerId(),
                DebtEventPayload.DEBT_OVERDUE_DETECTED, payload);
    }

    /**
     * Publica {@code DEBT_OVERDUE_CLEARED}.
     *
     * @param credit credito cuya deuda vencida quedo saldada
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishCleared(Credit credit) {
        DebtEventPayload payload = DebtEventPayload.builder()
                .customerId(credit.getCustomerId())
                .productType(PRODUCT_TYPE)
                .productId(credit.getId())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("debt-events"), credit.getCustomerId(),
                DebtEventPayload.DEBT_OVERDUE_CLEARED, payload);
    }
}
