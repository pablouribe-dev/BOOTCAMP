package com.bank.yanki.event;

import com.bank.yanki.config.BankKafkaProperties;
import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.model.YankiPayment;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publica las peticiones de operacion de cuenta y el resultado de los pagos
 * Yanki.
 */
@Component
@RequiredArgsConstructor
public class YankiEventPublisher {

    /** Retiro. */
    public static final String WITHDRAWAL = "WITHDRAWAL";

    /** Deposito. */
    public static final String DEPOSIT = "DEPOSIT";

    private final EventPublisher eventPublisher;
    private final BankKafkaProperties kafkaProperties;

    /**
     * Pide un deposito o retiro sobre la cuenta principal de una tarjeta.
     *
     * @param operationId identificador de la operacion
     * @param accountId   cuenta (clave del mensaje)
     * @param operation   {@code DEPOSIT} o {@code WITHDRAWAL}
     * @param amount      importe
     * @param paymentId   pago Yanki (referencia)
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable requestOperation(String operationId, String accountId, String operation, BigDecimal amount,
                                        String paymentId) {
        AccountOperationRequestedPayload payload = AccountOperationRequestedPayload.builder()
                .operationId(operationId)
                .accountId(accountId)
                .operation(operation)
                .amount(amount)
                .origin(AccountOperationRequestedPayload.ORIGIN)
                .reference(paymentId)
                .build();
        return eventPublisher.publish(kafkaProperties.topic("operation-requests"), accountId,
                AccountOperationRequestedPayload.ACCOUNT_OPERATION_REQUESTED, payload);
    }

    /**
     * Publica el resultado final del pago en {@code bank.yanki.events}.
     *
     * @param payment pago terminado
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishOutcome(YankiPayment payment) {
        YankiPaymentEventPayload payload = YankiPaymentEventPayload.builder()
                .paymentId(payment.getId())
                .fromPhoneNumber(payment.getFromPhoneNumber())
                .toPhoneNumber(payment.getToPhoneNumber())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .build();
        String eventType = payment.getStatus() == PaymentStatus.COMPLETED
                ? YankiPaymentEventPayload.YANKI_PAYMENT_COMPLETED
                : YankiPaymentEventPayload.YANKI_PAYMENT_FAILED;
        return eventPublisher.publish(kafkaProperties.topic("yanki-events"), payment.getId(), eventType, payload);
    }
}
