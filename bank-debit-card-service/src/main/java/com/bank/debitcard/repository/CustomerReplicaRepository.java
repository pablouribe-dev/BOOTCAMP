package com.bank.debitcard.repository;

import com.bank.debitcard.model.CustomerReplica;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio reactivo de la copia local de clientes.
 */
@Repository
public interface CustomerReplicaRepository extends ReactiveMongoRepository<CustomerReplica, String> {
}
