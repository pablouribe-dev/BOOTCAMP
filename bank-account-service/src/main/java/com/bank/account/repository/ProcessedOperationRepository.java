package com.bank.account.repository;

import com.bank.account.model.ProcessedOperation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio reactivo de las operaciones de cuenta ya procesadas.
 */
@Repository
public interface ProcessedOperationRepository extends ReactiveMongoRepository<ProcessedOperation, String> {
}
