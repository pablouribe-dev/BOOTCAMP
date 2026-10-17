package com.bank.creditcard.event;

import com.bank.creditcard.config.BankKafkaProperties;
import com.bank.creditcard.model.CreditCard;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publica en {@code bank.debt.events} la deteccion y la liquidacion de deudas
 * vencidas de tarjetas de credito, con el {@code customerId} como clave.
 */
@Component
@RequiredArgsConstructor
public class CardDebtEventPublisher {

    /** Tipo de producto que viaja en el payload. */
    public static final String PRODUCT_TYPE = "CREDIT_CARD";

    private final EventPublisher eventPublisher;
    private final BankKafkaProperties kafkaProperties;

    /**
     * Publica {@code DEBT_OVERDUE_DETECTED}.
     *
     * @param card tarjeta con deuda vencida
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishDetected(CreditCard card) {
        DebtEventPayload payload = DebtEventPayload.builder()
                .customerId(card.getCustomerId())
                .productType(PRODUCT_TYPE)
                .productId(card.getId())
                .dueDate(card.getPaymentDueDate())
                .dueAmount(card.getDueAmount())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("debt-events"), card.getCustomerId(),
                DebtEventPayload.DEBT_OVERDUE_DETECTED, payload);
    }

    /**
     * Publica {@code DEBT_OVERDUE_CLEARED}.
     *
     * @param card tarjeta cuya deuda vencida quedo saldada
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishCleared(CreditCard card) {
        DebtEventPayload payload = DebtEventPayload.builder()
                .customerId(card.getCustomerId())
                .productType(PRODUCT_TYPE)
                .productId(card.getId())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("debt-events"), card.getCustomerId(),
                DebtEventPayload.DEBT_OVERDUE_CLEARED, payload);
    }
}
