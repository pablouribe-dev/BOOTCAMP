package com.bank.debitcard.repository;

import com.bank.debitcard.model.DebitPayment;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de pagos con tarjeta de debito.
 */
@Repository
public interface DebitPaymentRepository extends ReactiveMongoRepository<DebitPayment, String> {

    /**
     * Pagos de una tarjeta, del mas reciente al mas antiguo.
     *
     * @param cardId identificador de la tarjeta
     * @return pagos de la tarjeta
     */
    Flux<DebitPayment> findByCardIdOrderByCreatedAtDesc(String cardId);

    /**
     * Pago de una tarjeta por id.
     *
     * @param id     identificador del pago
     * @param cardId identificador de la tarjeta
     * @return el pago, o vacio
     */
    Mono<DebitPayment> findByIdAndCardId(String id, String cardId);

    /**
     * Pago cuya operacion de cuenta en curso es la dada.
     *
     * @param currentOperationId identificador de la operacion
     * @return el pago, o vacio si la operacion no es de este servicio
     */
    Mono<DebitPayment> findByCurrentOperationId(String currentOperationId);
}
