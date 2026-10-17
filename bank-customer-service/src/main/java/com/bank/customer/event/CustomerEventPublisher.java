package com.bank.customer.event;

import com.bank.customer.config.BankKafkaProperties;
import com.bank.customer.model.Customer;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publica los snapshots de clientes en {@code bank.customer.events}.
 */
@Component
@RequiredArgsConstructor
public class CustomerEventPublisher {

    /** Evento con el snapshot completo del cliente. */
    public static final String CUSTOMER_UPSERTED = "CUSTOMER_UPSERTED";

    /** Evento de baja: solo lleva el id. */
    public static final String CUSTOMER_DELETED = "CUSTOMER_DELETED";

    private static final String TOPIC = "customer-events";

    private final EventPublisher eventPublisher;
    private final BankKafkaProperties kafkaProperties;

    /**
     * Publica el snapshot de un cliente creado o actualizado.
     *
     * @param customer cliente guardado
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishUpserted(Customer customer) {
        CustomerEventPayload payload = CustomerEventPayload.builder()
                .customerId(customer.getId())
                .type(customer.getType())
                .profile(customer.getProfile())
                .documentType(customer.getDocumentType())
                .documentNumber(customer.getDocumentNumber())
                .build();
        return eventPublisher.publish(kafkaProperties.topic(TOPIC), customer.getId(), CUSTOMER_UPSERTED, payload);
    }

    /**
     * Publica la baja de un cliente.
     *
     * @param customerId identificador del cliente eliminado
     * @return completable que termina cuando Kafka confirmo el mensaje
     */
    public Completable publishDeleted(String customerId) {
        return eventPublisher.publish(kafkaProperties.topic(TOPIC), customerId, CUSTOMER_DELETED,
                CustomerEventPayload.builder().customerId(customerId).build());
    }
}
