package com.bank.debitcard.repository;

import com.bank.debitcard.model.AccountReplica;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio reactivo de la copia local de cuentas.
 */
@Repository
public interface AccountReplicaRepository extends ReactiveMongoRepository<AccountReplica, String> {
}
