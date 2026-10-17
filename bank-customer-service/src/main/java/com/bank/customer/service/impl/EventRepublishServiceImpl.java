package com.bank.customer.service.impl;

import com.bank.customer.adapter.ReactorRxAdapter;
import com.bank.customer.dto.RepublishResponse;
import com.bank.customer.event.CustomerEventPublisher;
import com.bank.customer.repository.CustomerRepository;
import com.bank.customer.service.EventRepublishService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de la republicacion de snapshots de clientes.
 *
 * <p>A diferencia del CRUD, aqui un fallo de Kafka si se propaga: el
 * administrador necesita saber que la republicacion no termino.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRepublishServiceImpl implements EventRepublishService {

    private final CustomerRepository customerRepository;
    private final CustomerEventPublisher customerEventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<RepublishResponse> republishAll() {
        log.info("Republishing {} for every stored customer", CustomerEventPublisher.CUSTOMER_UPSERTED);
        return ReactorRxAdapter.fluxToFlowable(customerRepository.findAll())
                .concatMapSingle(customer -> customerEventPublisher.publishUpserted(customer).toSingleDefault(1))
                .count()
                .map(count -> RepublishResponse.builder()
                        .eventType(CustomerEventPublisher.CUSTOMER_UPSERTED)
                        .republished(count)
                        .build())
                .doOnSuccess(response -> log.info("Republished {} customer snapshots", response.getRepublished()));
    }
}
