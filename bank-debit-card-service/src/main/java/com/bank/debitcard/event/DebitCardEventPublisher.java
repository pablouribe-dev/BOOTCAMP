package com.bank.debitcard.event;

import com.bank.debitcard.config.BankKafkaProperties;
import com.bank.debitcard.model.DebitCard;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos de este servicio: snapshots de tarjetas en
 * {@code bank.debit-card.events} y peticiones de retiro en
 * {@code bank.account.operation-requests}.
 */
@Component
@RequiredArgsConstructor
public class DebitCardEventPublisher {

    private final EventPublisher eventPublisher;
    private final BankKafkaProperties kafkaProperties;

    /**
     * Publica el snapshot de una tarjeta.
     *
     * @param card tarjeta guardada
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishUpserted(DebitCard card) {
        DebitCardEventPayload payload = DebitCardEventPayload.builder()
                .cardId(card.getId())
                .cardNumber(card.getCardNumber())
                .customerId(card.getCustomerId())
                .accountIds(List.copyOf(card.getAccountIds()))
                .mainAccountId(card.getMainAccountId())
                .status(card.getStatus().name())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("debit-card-events"), card.getId(),
                DebitCardEventPayload.DEBIT_CARD_UPSERTED, payload);
    }

    /**
     * Publica la baja de una tarjeta.
     *
     * @param card tarjeta eliminada
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishDeleted(DebitCard card) {
        DebitCardEventPayload payload = DebitCardEventPayload.builder()
                .cardId(card.getId())
                .cardNumber(card.getCardNumber())
                .customerId(card.getCustomerId())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("debit-card-events"), card.getId(),
                DebitCardEventPayload.DEBIT_CARD_DELETED, payload);
    }

    /**
     * Pide un retiro a una cuenta, con la cuenta como clave para que sus
     * operaciones se procesen en orden.
     *
     * @param operationId identificador de la operacion
     * @param accountId   cuenta a cargar
     * @param amount      importe
     * @param cardId      tarjeta que origina el pago (referencia)
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable requestWithdrawal(String operationId, String accountId, BigDecimal amount, String cardId) {
        AccountOperationRequestedPayload payload = AccountOperationRequestedPayload.builder()
                .operationId(operationId)
                .accountId(accountId)
                .operation("WITHDRAWAL")
                .amount(amount)
                .origin(AccountOperationRequestedPayload.ORIGIN)
                .reference(cardId)
                .build();
        return eventPublisher.publish(kafkaProperties.topic("operation-requests"), accountId,
                AccountOperationRequestedPayload.ACCOUNT_OPERATION_REQUESTED, payload);
    }
}
