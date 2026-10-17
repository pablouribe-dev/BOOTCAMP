package com.bank.debitcard.service.impl;

import com.bank.debitcard.adapter.ReactorRxAdapter;
import com.bank.debitcard.event.AccountEventPayload;
import com.bank.debitcard.event.CustomerEventPayload;
import com.bank.debitcard.model.AccountReplica;
import com.bank.debitcard.model.CustomerReplica;
import com.bank.debitcard.repository.AccountReplicaRepository;
import com.bank.debitcard.repository.CustomerReplicaRepository;
import com.bank.debitcard.service.ReplicaService;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion de las copias locales. Los snapshots usan el id del agregado
 * como {@code _id}, asi que repetir un evento reemplaza el mismo documento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicaServiceImpl implements ReplicaService {

    private final CustomerReplicaRepository customerReplicaRepository;
    private final AccountReplicaRepository accountReplicaRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable upsertCustomer(CustomerEventPayload payload) {
        CustomerReplica replica = CustomerReplica.builder()
                .customerId(payload.getCustomerId())
                .type(payload.getType())
                .profile(payload.getProfile())
                .documentType(payload.getDocumentType())
                .documentNumber(payload.getDocumentNumber())
                .updatedAt(Instant.now())
                .build();
        return ReactorRxAdapter.monoToCompletable(customerReplicaRepository.save(replica))
                .doOnComplete(() -> log.info("Customer replica {} updated", payload.getCustomerId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable deleteCustomer(String customerId) {
        return ReactorRxAdapter.monoToCompletable(customerReplicaRepository.deleteById(customerId))
                .doOnComplete(() -> log.info("Customer replica {} deleted", customerId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable upsertAccount(AccountEventPayload payload) {
        AccountReplica replica = AccountReplica.builder()
                .accountId(payload.getAccountId())
                .customerId(payload.getCustomerId())
                .accountType(payload.getAccountType())
                .accountNumber(payload.getAccountNumber())
                .profile(payload.getProfile())
                .updatedAt(Instant.now())
                .build();
        return ReactorRxAdapter.monoToCompletable(accountReplicaRepository.save(replica))
                .doOnComplete(() -> log.info("Account replica {} updated", payload.getAccountId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable deleteAccount(String accountId) {
        return ReactorRxAdapter.monoToCompletable(accountReplicaRepository.deleteById(accountId))
                .doOnComplete(() -> log.info("Account replica {} deleted", accountId));
    }
}
