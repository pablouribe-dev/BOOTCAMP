package com.bank.debitcard.repository;

import com.bank.debitcard.event.ProcessedEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio reactivo de los eventos ya procesados.
 */
@Repository
public interface ProcessedEventRepository extends ReactiveMongoRepository<ProcessedEvent, String> {
}
