package com.bank.customer.repository;

import com.bank.customer.event.ProcessedEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio reactivo de los eventos ya procesados.
 */
@Repository
public interface ProcessedEventRepository extends ReactiveMongoRepository<ProcessedEvent, String> {
}
