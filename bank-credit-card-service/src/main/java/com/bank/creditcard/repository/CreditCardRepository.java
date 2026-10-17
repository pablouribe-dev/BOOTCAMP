package com.bank.creditcard.repository;

import com.bank.creditcard.model.CreditCard;
import java.time.LocalDate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de tarjetas de credito.
 *
 * <p>Solo consultas derivadas del nombre del metodo: no se usa {@code @Query}
 * ni criterios armados a mano.</p>
 */
@Repository
public interface CreditCardRepository extends ReactiveMongoRepository<CreditCard, String> {

    /**
     * Lista las tarjetas de un cliente.
     *
     * @param customerId identificador del cliente
     * @return flujo de tarjetas del cliente
     */
    Flux<CreditCard> findByCustomerId(String customerId);

    /**
     * Parte III: tarjetas con fecha limite de pago anterior a la fecha dada que
     * aun no fueron notificadas como vencidas ({@code overdueNotified} distinto
     * de {@code true}, incluido nulo). El importe se filtra en el servicio.
     *
     * @param date     fecha de corte (hoy)
     * @param notified valor a excluir ({@code true})
     * @return tarjetas candidatas
     */
    Flux<CreditCard> findByPaymentDueDateBeforeAndOverdueNotifiedNot(LocalDate date, Boolean notified);

    /**
     * Busca una tarjeta por su numero.
     *
     * @param cardNumber numero de tarjeta
     * @return la tarjeta, o vacio si no existe
     */
    Mono<CreditCard> findByCardNumber(String cardNumber);

    /**
     * Indica si un numero de tarjeta ya esta en uso.
     *
     * @param cardNumber numero de tarjeta generado
     * @return {@code true} si el numero ya existe
     */
    Mono<Boolean> existsByCardNumber(String cardNumber);
}
