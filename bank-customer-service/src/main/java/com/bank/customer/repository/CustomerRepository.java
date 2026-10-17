package com.bank.customer.repository;

import com.bank.customer.model.Customer;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de clientes.
 *
 * <p>Solo consultas derivadas del nombre del metodo: no se usa {@code @Query}
 * ni criterios armados a mano.</p>
 */
@Repository
public interface CustomerRepository extends ReactiveMongoRepository<Customer, String> {

    /**
     * Indica si ya existe un cliente con el numero de documento dado.
     *
     * @param documentNumber numero de documento a verificar
     * @return {@code true} si el documento ya esta registrado
     */
    Mono<Boolean> existsByDocumentNumber(String documentNumber);

    /**
     * Busca un cliente por su numero de documento.
     *
     * @param documentNumber numero de documento
     * @return el cliente, o vacio si no existe
     */
    Mono<Customer> findByDocumentNumber(String documentNumber);
}
