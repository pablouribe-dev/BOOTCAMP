package com.bank.yanki.repository;

import com.bank.yanki.model.DebitCardReplica;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la copia local de tarjetas de debito.
 */
@Repository
public interface DebitCardReplicaRepository extends ReactiveMongoRepository<DebitCardReplica, String> {

    /**
     * Busca una tarjeta por numero.
     *
     * @param cardNumber numero de tarjeta
     * @return la copia, o vacio
     */
    Mono<DebitCardReplica> findByCardNumber(String cardNumber);
}
