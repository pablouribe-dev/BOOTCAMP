package com.bank.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.customer.event.CustomerEventPublisher;
import com.bank.customer.model.Customer;
import com.bank.customer.repository.CustomerRepository;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class EventRepublishServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerEventPublisher customerEventPublisher;

    @InjectMocks
    private EventRepublishServiceImpl service;

    @Test
    void republishesOneSnapshotPerStoredCustomer() {
        when(customerRepository.findAll()).thenReturn(Flux.just(Customer.builder().id("c1").build(),
                Customer.builder().id("c2").build()));
        when(customerEventPublisher.publishUpserted(any())).thenReturn(Completable.complete());

        service.republishAll().test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getRepublished() == 2
                        && "CUSTOMER_UPSERTED".equals(response.getEventType()));
        verify(customerEventPublisher, times(2)).publishUpserted(any());
    }

    @Test
    void reportsKafkaFailuresToTheAdministrator() {
        when(customerRepository.findAll()).thenReturn(Flux.just(Customer.builder().id("c1").build()));
        when(customerEventPublisher.publishUpserted(any()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.republishAll().test().awaitDone(1, TimeUnit.SECONDS).assertError(IllegalStateException.class);
    }
}
