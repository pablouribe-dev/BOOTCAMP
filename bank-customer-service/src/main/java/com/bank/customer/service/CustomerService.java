package com.bank.customer.service;

import com.bank.customer.dto.CustomerRequest;
import com.bank.customer.dto.CustomerResponse;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Reglas de negocio del CRUD de clientes.
 *
 * <p>Expone tipos de RxJava 3; la conversion desde {@code Mono}/{@code Flux}
 * del repositorio ocurre en la implementacion.</p>
 */
public interface CustomerService {

    /**
     * Registra un cliente nuevo.
     *
     * @param request datos del cliente
     * @return el cliente creado
     */
    Single<CustomerResponse> create(CustomerRequest request);

    /**
     * Lista todos los clientes registrados.
     *
     * @return flujo de clientes
     */
    Flowable<CustomerResponse> findAll();

    /**
     * Busca un cliente por numero de documento (con cache Redis).
     *
     * @param documentNumber numero de documento
     * @return el cliente, o vacio si no existe
     */
    Maybe<CustomerResponse> findByDocumentNumber(String documentNumber);

    /**
     * Busca un cliente por su identificador.
     *
     * @param id identificador del cliente
     * @return el cliente encontrado
     */
    Single<CustomerResponse> findById(String id);

    /**
     * Actualiza los datos de un cliente existente.
     *
     * @param id      identificador del cliente
     * @param request datos nuevos
     * @return el cliente actualizado
     */
    Single<CustomerResponse> update(String id, CustomerRequest request);

    /**
     * Elimina un cliente.
     *
     * @param id identificador del cliente
     * @return completable que termina cuando el cliente fue eliminado
     */
    Completable delete(String id);
}
