package com.bank.account.service.impl;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.dto.RepublishResponse;
import com.bank.account.event.AccountEventPublisher;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.EventRepublishService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de la republicacion de snapshots de cuentas.
 *
 * <p>A diferencia del CRUD, aqui un fallo de Kafka si se propaga: el
 * administrador necesita saber que la republicacion no termino.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRepublishServiceImpl implements EventRepublishService {

    private final AccountRepository accountRepository;
    private final AccountEventPublisher accountEventPublisher;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<RepublishResponse> republishAll() {
        log.info("Republishing {} for every stored account", AccountEventPublisher.ACCOUNT_UPSERTED);
        return ReactorRxAdapter.fluxToFlowable(accountRepository.findAll())
                .concatMapSingle(account -> accountEventPublisher.publishUpserted(account).toSingleDefault(1))
                .count()
                .map(count -> RepublishResponse.builder()
                        .eventType(AccountEventPublisher.ACCOUNT_UPSERTED)
                        .republished(count)
                        .build())
                .doOnSuccess(response -> log.info("Republished {} account snapshots", response.getRepublished()));
    }
}
