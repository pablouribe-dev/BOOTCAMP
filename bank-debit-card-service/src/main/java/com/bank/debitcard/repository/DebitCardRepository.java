package com.bank.debitcard.repository;

import com.bank.debitcard.model.DebitCard;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de tarjetas de debito.
 */
@Repository
public interface DebitCardRepository extends ReactiveMongoRepository<DebitCard, String> {

    /**
     * Tarjetas de un cliente.
     *
     * @param customerId identificador del cliente
     * @return tarjetas del cliente
     */
    Flux<DebitCard> findByCustomerId(String customerId);

    /**
     * Tarjetas que tienen asociada una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @return tarjetas que la incluyen
     */
    Flux<DebitCard> findByAccountIdsContaining(String accountId);

    /**
     * Indica si un numero de tarjeta ya existe.
     *
     * @param cardNumber numero de tarjeta
     * @return {@code true} si existe
     */
    Mono<Boolean> existsByCardNumber(String cardNumber);
}
