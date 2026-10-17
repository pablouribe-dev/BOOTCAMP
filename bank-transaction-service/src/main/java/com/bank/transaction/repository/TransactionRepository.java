package com.bank.transaction.repository;

import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionOrigin;
import com.bank.transaction.model.TransactionType;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de movimientos.
 *
 * <p>Solo consultas derivadas del nombre del metodo: no se usa {@code @Query}
 * ni criterios armados a mano.</p>
 */
@Repository
public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {

    /**
     * Lista los movimientos de un producto.
     *
     * @param productId identificador del producto
     * @return flujo de movimientos del producto
     */
    Flux<Transaction> findByProductId(String productId);

    /**
     * Cuenta todos los movimientos de un producto dentro de un rango de fechas.
     *
     * <p>Base de las reglas duras de la Parte I: limite mensual de la cuenta de
     * ahorro y movimiento unico del plazo fijo (las transferencias tambien
     * cuentan). Parte III: excluye los origenes dados (movimientos por eventos).</p>
     *
     * @param productId identificador del producto
     * @param origins   origenes que no cuentan
     * @param start     inicio del rango, inclusive
     * @param end       fin del rango, inclusive
     * @return cantidad de movimientos encontrados
     */
    Mono<Long> countByProductIdAndOriginNotInAndCreatedAtBetween(String productId,
                                                              Collection<TransactionOrigin> origins,
                                                              LocalDateTime start,
                                                              LocalDateTime end);

    /**
     * Cuenta los movimientos de ciertos tipos de un producto en un rango.
     *
     * <p>Base de la comision por exceso: solo cuentan depositos y retiros.</p>
     *
     * @param productId identificador del producto
     * @param types     tipos de movimiento a contar
     * @param origins   origenes que no cuentan (Parte III)
     * @param start     inicio del rango, inclusive
     * @param end       fin del rango, inclusive
     * @return cantidad de movimientos encontrados
     */
    Mono<Long> countByProductIdAndTransactionTypeInAndOriginNotInAndCreatedAtBetween(
            String productId, Collection<TransactionType> types, Collection<TransactionOrigin> origins,
            LocalDateTime start, LocalDateTime end);

    /**
     * Lista los movimientos de un producto en un rango, del mas antiguo al mas
     * reciente. Base del reporte de producto.
     *
     * @param productId identificador del producto
     * @param start     inicio del rango, inclusive
     * @param end       fin del rango, inclusive
     * @return flujo de movimientos
     */
    Flux<Transaction> findByProductIdAndCreatedAtBetweenOrderByCreatedAtAsc(String productId,
                                                                            LocalDateTime start,
                                                                            LocalDateTime end);

    /**
     * Lista todos los movimientos de un rango. Base del reporte general.
     *
     * @param start inicio del rango, inclusive
     * @param end   fin del rango, inclusive
     * @return flujo de movimientos
     */
    Flux<Transaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Los ultimos 10 movimientos de un producto, del mas reciente al mas
     * antiguo.
     *
     * @param productType tipo de producto
     * @param productId   identificador del producto
     * @return hasta 10 movimientos
     */
    Flux<Transaction> findTop10ByProductTypeAndProductIdOrderByCreatedAtDesc(ProductType productType,
                                                                             String productId);

    /**
     * Parte III: indica si ya se registro el movimiento de una operacion de
     * cuenta recibida por Kafka.
     *
     * @param operationId identificador de la operacion
     * @return {@code true} si ya existe
     */
    Mono<Boolean> existsByOperationId(String operationId);
}
