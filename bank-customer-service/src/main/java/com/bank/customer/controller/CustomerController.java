package com.bank.customer.controller;

import com.bank.customer.dto.CustomerRequest;
import com.bank.customer.dto.CustomerResponse;
import com.bank.customer.service.CustomerService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST del CRUD de clientes.
 */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Registra un cliente nuevo.
     *
     * @param request datos del cliente
     * @return el cliente creado, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    /**
     * Lista todos los clientes, o busca uno por numero de documento.
     *
     * @param documentNumber filtro opcional por numero de documento (con cache Redis)
     * @return flujo de clientes, con estado 200
     */
    @GetMapping
    public Flowable<CustomerResponse> findAll(@RequestParam(required = false) String documentNumber) {
        if (documentNumber != null && !documentNumber.isBlank()) {
            return customerService.findByDocumentNumber(documentNumber).toFlowable();
        }
        return customerService.findAll();
    }

    /**
     * Busca un cliente por su identificador.
     *
     * @param id identificador del cliente
     * @return el cliente encontrado, con estado 200
     */
    @GetMapping("/{id}")
    public Single<CustomerResponse> findById(@PathVariable String id) {
        return customerService.findById(id);
    }

    /**
     * Actualiza un cliente existente.
     *
     * @param id      identificador del cliente
     * @param request datos nuevos
     * @return el cliente actualizado, con estado 200
     */
    @PutMapping("/{id}")
    public Single<CustomerResponse> update(@PathVariable String id,
                                           @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    /**
     * Elimina un cliente.
     *
     * @param id identificador del cliente
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return customerService.delete(id);
    }
}
