package com.bank.debitcard.repository;

import com.bank.debitcard.model.OverdueDebt;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de la copia local de deudas vencidas.
 */
@Repository
public interface OverdueDebtRepository extends ReactiveMongoRepository<OverdueDebt, String> {

    /**
     * Indica si el cliente tiene alguna deuda vencida registrada.
     *
     * @param customerId identificador del cliente
     * @return {@code true} si tiene al menos una
     */
    Mono<Boolean> existsByCustomerId(String customerId);

    /**
     * Busca la deuda de un producto.
     *
     * @param productType tipo de producto
     * @param productId   identificador del producto
     * @return la deuda, o vacio
     */
    Mono<OverdueDebt> findByProductTypeAndProductId(String productType, String productId);

    /**
     * Elimina la deuda de un producto.
     *
     * @param productType tipo de producto
     * @param productId   identificador del producto
     * @return cantidad de documentos eliminados
     */
    Mono<Long> deleteByProductTypeAndProductId(String productType, String productId);
}
