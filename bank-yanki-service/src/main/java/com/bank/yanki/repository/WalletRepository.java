package com.bank.yanki.repository;

import com.bank.yanki.model.Wallet;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de monederos.
 */
@Repository
public interface WalletRepository extends ReactiveMongoRepository<Wallet, String> {

    /**
     * Busca un monedero por celular.
     *
     * @param phoneNumber celular
     * @return el monedero, o vacio
     */
    Mono<Wallet> findByPhoneNumber(String phoneNumber);

    /**
     * Indica si el celular ya esta registrado.
     *
     * @param phoneNumber celular
     * @return {@code true} si existe
     */
    Mono<Boolean> existsByPhoneNumber(String phoneNumber);

    /**
     * Indica si el documento ya esta registrado.
     *
     * @param documentNumber numero de documento
     * @return {@code true} si existe
     */
    Mono<Boolean> existsByDocumentNumber(String documentNumber);
}
