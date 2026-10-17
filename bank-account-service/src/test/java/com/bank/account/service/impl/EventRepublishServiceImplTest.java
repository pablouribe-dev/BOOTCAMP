package com.bank.account.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.event.AccountEventPublisher;
import com.bank.account.model.Account;
import com.bank.account.repository.AccountRepository;
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
    private AccountRepository accountRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    @InjectMocks
    private EventRepublishServiceImpl service;

    @Test
    void republishesOneSnapshotPerStoredAccount() {
        when(accountRepository.findAll()).thenReturn(Flux.just(Account.builder().id("c1").build(),
                Account.builder().id("c2").build()));
        when(accountEventPublisher.publishUpserted(any())).thenReturn(Completable.complete());

        service.republishAll().test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getRepublished() == 2
                        && "ACCOUNT_UPSERTED".equals(response.getEventType()));
        verify(accountEventPublisher, times(2)).publishUpserted(any());
    }

    @Test
    void reportsKafkaFailuresToTheAdministrator() {
        when(accountRepository.findAll()).thenReturn(Flux.just(Account.builder().id("c1").build()));
        when(accountEventPublisher.publishUpserted(any()))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.republishAll().test().awaitDone(1, TimeUnit.SECONDS).assertError(IllegalStateException.class);
    }
}
