package com.bank.debitcard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.debitcard.event.AccountEventPayload;
import com.bank.debitcard.event.CustomerEventPayload;
import com.bank.debitcard.model.AccountReplica;
import com.bank.debitcard.model.CustomerReplica;
import com.bank.debitcard.repository.AccountReplicaRepository;
import com.bank.debitcard.repository.CustomerReplicaRepository;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ReplicaServiceImplTest {

    @Mock
    private CustomerReplicaRepository customerReplicaRepository;

    @Mock
    private AccountReplicaRepository accountReplicaRepository;

    @InjectMocks
    private ReplicaServiceImpl service;

    @Test
    void customerSnapshotsReplaceTheCopyWithTheSameId() {
        when(customerReplicaRepository.save(any(CustomerReplica.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(customerReplicaRepository.deleteById("c1")).thenReturn(Mono.empty());

        service.upsertCustomer(CustomerEventPayload.builder().customerId("c1").type("PERSONAL").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.deleteCustomer("c1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<CustomerReplica> captor = ArgumentCaptor.forClass(CustomerReplica.class);
        verify(customerReplicaRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo("c1");
        assertThat(captor.getValue().getType()).isEqualTo("PERSONAL");
    }

    @Test
    void accountSnapshotsReplaceTheCopyWithTheSameId() {
        when(accountReplicaRepository.save(any(AccountReplica.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(accountReplicaRepository.deleteById("a1")).thenReturn(Mono.empty());

        service.upsertAccount(AccountEventPayload.builder().accountId("a1").customerId("c1").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        service.deleteAccount("a1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        ArgumentCaptor<AccountReplica> captor = ArgumentCaptor.forClass(AccountReplica.class);
        verify(accountReplicaRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo("c1");
    }
}
