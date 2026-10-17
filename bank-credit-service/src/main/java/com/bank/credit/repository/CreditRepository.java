package com.bank.credit.repository;

import com.bank.credit.model.Credit;
import java.time.LocalDate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de creditos.
 *
 * <p>Solo consultas derivadas del nombre del metodo: no se usa {@code @Query}
 * ni criterios armados a mano.</p>
 */
@Repository
public interface CreditRepository extends ReactiveMongoRepository<Credit, String> {

    /**
     * Lista los creditos de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de creditos del cliente
     */
    Flux<Credit> findByCustomerId(String customerId);

    /**
     * Parte III: creditos con vencimiento anterior a la fecha dada que aun no
     * fueron notificados como vencidos ({@code overdueNotified} distinto de
     * {@code true}, incluido nulo). El importe se filtra en el servicio.
     *
     * @param date     fecha de corte (hoy)
     * @param notified valor a excluir ({@code true})
     * @return creditos candidatos
     */
    Flux<Credit> findByDueDateBeforeAndOverdueNotifiedNot(LocalDate date, Boolean notified);

    /**
     * Cuenta los creditos de un cliente.
     *
     * <p>Base de la regla que limita la cantidad de creditos de un cliente
     * personal.</p>
     *
     * @param customerId identificador del cliente
     * @return cantidad de creditos encontrados
     */
    Mono<Long> countByCustomerId(String customerId);
}
