package com.bank.account.event;

import com.bank.account.config.BankKafkaProperties;
import com.bank.account.model.Account;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos de cuentas: snapshots en {@code bank.account.events} y
 * resultados de operaciones en {@code bank.account.operation-results}.
 */
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    /** Evento con el snapshot de la cuenta. */
    public static final String ACCOUNT_UPSERTED = "ACCOUNT_UPSERTED";

    /** Evento de baja de la cuenta. */
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";

    private final EventPublisher eventPublisher;
    private final BankKafkaProperties kafkaProperties;

    /**
     * Publica el snapshot de una cuenta creada o actualizada.
     *
     * @param account cuenta guardada
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishUpserted(Account account) {
        AccountEventPayload payload = AccountEventPayload.builder()
                .accountId(account.getId())
                .customerId(account.getCustomerId())
                .accountType(account.getAccountType())
                .accountNumber(account.getAccountNumber())
                .profile(account.getProfile())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("account-events"), account.getId(),
                ACCOUNT_UPSERTED, payload);
    }

    /**
     * Publica la baja de una cuenta.
     *
     * @param account cuenta eliminada
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishDeleted(Account account) {
        AccountEventPayload payload = AccountEventPayload.builder()
                .accountId(account.getId())
                .customerId(account.getCustomerId())
                .build();
        return eventPublisher.publish(kafkaProperties.topic("account-events"), account.getId(),
                ACCOUNT_DELETED, payload);
    }

    /**
     * Publica el resultado de una operacion pedida por Kafka, con la cuenta
     * como clave.
     *
     * @param eventType {@code ACCOUNT_OPERATION_COMPLETED} o {@code ACCOUNT_OPERATION_REJECTED}
     * @param result    resultado de la operacion
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishOperationResult(String eventType, AccountOperationResultPayload result) {
        return eventPublisher.publish(kafkaProperties.topic("operation-results"), result.getAccountId(),
                eventType, result);
    }
}
