package com.bank.customer.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.customer.config.BankKafkaProperties;
import com.bank.customer.model.Customer;
import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.model.DocumentType;
import io.reactivex.rxjava3.core.Completable;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private CustomerEventPublisher publisher;

    @BeforeEach
    void setUp() {
        BankKafkaProperties properties = new BankKafkaProperties();
        properties.setTopics(Map.of("customer-events", "bank.customer.events"));
        publisher = new CustomerEventPublisher(eventPublisher, properties);
    }

    @Test
    void publishesTheFullSnapshotKeyedByCustomerId() {
        when(eventPublisher.publish(eq("bank.customer.events"), eq("c1"), eq("CUSTOMER_UPSERTED"), any()))
                .thenReturn(Completable.complete());
        Customer customer = Customer.builder().id("c1").type(CustomerType.PERSONAL).profile(CustomerProfile.VIP)
                .documentType(DocumentType.DNI).documentNumber("123").build();

        publisher.publishUpserted(customer).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(eq("bank.customer.events"), eq("c1"), eq("CUSTOMER_UPSERTED"),
                payload.capture());
        assertThat(payload.getValue()).isEqualTo(CustomerEventPayload.builder().customerId("c1")
                .type(CustomerType.PERSONAL).profile(CustomerProfile.VIP).documentType(DocumentType.DNI)
                .documentNumber("123").build());
    }

    @Test
    void publishesTheDeletionWithOnlyTheId() {
        when(eventPublisher.publish(eq("bank.customer.events"), eq("c1"), eq("CUSTOMER_DELETED"), any()))
                .thenReturn(Completable.complete());

        publisher.publishDeleted("c1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(eventPublisher).publish("bank.customer.events", "c1", "CUSTOMER_DELETED",
                CustomerEventPayload.builder().customerId("c1").build());
    }
}
