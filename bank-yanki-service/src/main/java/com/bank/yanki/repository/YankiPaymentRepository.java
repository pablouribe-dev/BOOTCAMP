package com.bank.yanki.repository;

import com.bank.yanki.model.PaymentStatus;
import com.bank.yanki.model.YankiPayment;
import java.time.LocalDateTime;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de pagos Yanki.
 */
@Repository
public interface YankiPaymentRepository extends ReactiveMongoRepository<YankiPayment, String> {

    /**
     * Busca el pago al que pertenece una operacion de cuenta (debito, credito o
     * compensacion).
     *
     * @param debitOperationId        id de operacion
     * @param creditOperationId       el mismo id
     * @param compensationOperationId el mismo id
     * @return el pago, o vacio si la operacion no es de Yanki
     */
    Mono<YankiPayment> findFirstByDebitOperationIdOrCreditOperationIdOrCompensationOperationId(
            String debitOperationId, String creditOperationId, String compensationOperationId);

    /**
     * Pagos en un estado creados antes de un momento (pagos colgados).
     *
     * @param status estado
     * @param before limite
     * @return pagos encontrados
     */
    Flux<YankiPayment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);

    /**
     * Historial de un celular, como emisor o receptor.
     *
     * @param fromPhoneNumber celular
     * @param toPhoneNumber   el mismo celular
     * @return pagos, del mas reciente al mas antiguo
     */
    Flux<YankiPayment> findByFromPhoneNumberOrToPhoneNumberOrderByCreatedAtDesc(String fromPhoneNumber,
                                                                              String toPhoneNumber);

    /**
     * Todos los pagos, del mas reciente al mas antiguo.
     *
     * @return pagos
     */
    Flux<YankiPayment> findAllByOrderByCreatedAtDesc();
}
