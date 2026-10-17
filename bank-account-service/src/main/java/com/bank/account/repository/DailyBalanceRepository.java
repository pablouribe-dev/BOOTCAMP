package com.bank.account.repository;

import com.bank.account.model.DailyBalance;
import java.time.LocalDate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de saldos diarios.
 *
 * <p>Solo consultas derivadas del nombre del metodo: no se usa {@code @Query}.</p>
 */
@Repository
public interface DailyBalanceRepository extends ReactiveMongoRepository<DailyBalance, String> {

    /**
     * Busca el registro de una cuenta en un dia.
     *
     * @param accountId identificador de la cuenta
     * @param date      dia
     * @return el registro, o vacio si ese dia no hubo operaciones
     */
    Mono<DailyBalance> findByAccountIdAndDate(String accountId, LocalDate date);

    /**
     * Lista los registros de una cuenta hasta una fecha, del mas antiguo al mas
     * reciente. Base del calculo del promedio diario.
     *
     * @param accountId identificador de la cuenta
     * @param date      ultimo dia incluido
     * @return flujo de registros ordenados por fecha
     */
    Flux<DailyBalance> findByAccountIdAndDateLessThanEqualOrderByDateAsc(String accountId, LocalDate date);

    /**
     * Elimina todos los registros de una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @return completa cuando se eliminaron
     */
    Mono<Void> deleteByAccountId(String accountId);
}
