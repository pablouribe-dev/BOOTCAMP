package com.bank.creditcard.controller;

import com.bank.creditcard.dto.CardBalanceResponse;
import com.bank.creditcard.dto.CardOperationRequest;
import com.bank.creditcard.dto.CreditCardRequest;
import com.bank.creditcard.dto.CreditCardResponse;
import com.bank.creditcard.service.CreditCardService;
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
 * Endpoints REST de las tarjetas de credito.
 */
@RestController
@RequestMapping("/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;

    /**
     * Emite una tarjeta nueva.
     *
     * @param request datos de la tarjeta
     * @return la tarjeta creada, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<CreditCardResponse> create(@Valid @RequestBody CreditCardRequest request) {
        return creditCardService.create(request);
    }

    /**
     * Lista las tarjetas, opcionalmente filtradas por cliente.
     *
     * @param customerId filtro opcional por cliente
     * @return flujo de tarjetas, con estado 200
     */
    @GetMapping
    public Flowable<CreditCardResponse> findAll(@RequestParam(required = false) String customerId) {
        return creditCardService.findAll(customerId);
    }

    /**
     * Busca una tarjeta por su identificador.
     *
     * @param id identificador de la tarjeta
     * @return la tarjeta encontrada, con estado 200
     */
    @GetMapping("/{id}")
    public Single<CreditCardResponse> findById(@PathVariable String id) {
        return creditCardService.findById(id);
    }

    /**
     * Actualiza los datos editables de una tarjeta.
     *
     * @param id      identificador de la tarjeta
     * @param request datos nuevos
     * @return la tarjeta actualizada, con estado 200
     */
    @PutMapping("/{id}")
    public Single<CreditCardResponse> update(@PathVariable String id,
                                             @Valid @RequestBody CreditCardRequest request) {
        return creditCardService.update(id, request);
    }

    /**
     * Elimina una tarjeta.
     *
     * @param id identificador de la tarjeta
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return creditCardService.delete(id);
    }

    /**
     * Consulta el saldo disponible de una tarjeta.
     *
     * @param id identificador de la tarjeta
     * @return linea, consumo y saldo disponible, con estado 200
     */
    @GetMapping("/{id}/balance")
    public Single<CardBalanceResponse> getBalance(@PathVariable String id) {
        return creditCardService.getBalance(id);
    }

    /**
     * Aplica un consumo o un pago sobre el importe usado de la tarjeta.
     *
     * <p>Endpoint interno: lo invoca {@code bank-transaction-service} y rechaza
     * el consumo si excede la linea disponible.</p>
     *
     * @param id      identificador de la tarjeta
     * @param request operacion e importe
     * @return saldo disponible resultante, con estado 200
     */
    @PostMapping("/{id}/balance-operations")
    public Single<CardBalanceResponse> applyBalanceOperation(
            @PathVariable String id,
            @Valid @RequestBody CardOperationRequest request) {
        return creditCardService.applyBalanceOperation(id, request);
    }
}
