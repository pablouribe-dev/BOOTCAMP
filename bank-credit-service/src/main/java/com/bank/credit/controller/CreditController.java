package com.bank.credit.controller;

import com.bank.credit.dto.CreditBalanceResponse;
import com.bank.credit.dto.CreditOperationRequest;
import com.bank.credit.dto.CreditRequest;
import com.bank.credit.dto.CreditResponse;
import com.bank.credit.service.CreditService;
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
 * Endpoints REST de los creditos.
 */
@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /**
     * Otorga un credito nuevo.
     *
     * @param request datos del credito
     * @return el credito creado, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<CreditResponse> create(@Valid @RequestBody CreditRequest request) {
        return creditService.create(request);
    }

    /**
     * Lista los creditos, opcionalmente filtrados por cliente.
     *
     * @param customerId filtro opcional por cliente
     * @return flujo de creditos, con estado 200
     */
    @GetMapping
    public Flowable<CreditResponse> findAll(@RequestParam(required = false) String customerId) {
        return creditService.findAll(customerId);
    }

    /**
     * Busca un credito por su identificador.
     *
     * @param id identificador del credito
     * @return el credito encontrado, con estado 200
     */
    @GetMapping("/{id}")
    public Single<CreditResponse> findById(@PathVariable String id) {
        return creditService.findById(id);
    }

    /**
     * Actualiza los datos editables de un credito.
     *
     * @param id      identificador del credito
     * @param request datos nuevos
     * @return el credito actualizado, con estado 200
     */
    @PutMapping("/{id}")
    public Single<CreditResponse> update(@PathVariable String id,
                                         @Valid @RequestBody CreditRequest request) {
        return creditService.update(id, request);
    }

    /**
     * Elimina un credito.
     *
     * @param id identificador del credito
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return creditService.delete(id);
    }

    /**
     * Consulta el saldo pendiente de un credito.
     *
     * @param id identificador del credito
     * @return saldo pendiente, con estado 200
     */
    @GetMapping("/{id}/balance")
    public Single<CreditBalanceResponse> getBalance(@PathVariable String id) {
        return creditService.getBalance(id);
    }

    /**
     * Aplica un pago sobre el saldo pendiente del credito.
     *
     * <p>Endpoint interno: lo invoca {@code bank-transaction-service} y rechaza
     * el pago si supera el saldo pendiente.</p>
     *
     * @param id      identificador del credito
     * @param request operacion e importe
     * @return saldo pendiente resultante, con estado 200
     */
    @PostMapping("/{id}/balance-operations")
    public Single<CreditBalanceResponse> applyBalanceOperation(
            @PathVariable String id,
            @Valid @RequestBody CreditOperationRequest request) {
        return creditService.applyBalanceOperation(id, request);
    }
}
