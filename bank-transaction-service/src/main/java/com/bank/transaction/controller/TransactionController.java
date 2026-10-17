package com.bank.transaction.controller;

import com.bank.transaction.dto.AccountTransactionRequest;
import com.bank.transaction.dto.ChargeRequest;
import com.bank.transaction.dto.PaymentRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.dto.TransactionUpdateRequest;
import com.bank.transaction.dto.TransferRequest;
import com.bank.transaction.dto.TransferResponse;
import com.bank.transaction.service.TransactionService;
import com.bank.transaction.service.TransferService;
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
 * Endpoints REST de los movimientos.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransferService transferService;

    /**
     * Transfiere dinero entre cuentas del banco (propias o de terceros).
     *
     * @param request cuenta origen, numero de cuenta destino, importe y descripcion
     * @return los dos movimientos registrados, con estado 201
     */
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return transferService.transfer(request);
    }

    /**
     * Registra un deposito en una cuenta.
     *
     * @param request cuenta, importe y descripcion
     * @return el movimiento registrado, con estado 201
     */
    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionResponse> deposit(@Valid @RequestBody AccountTransactionRequest request) {
        return transactionService.deposit(request);
    }

    /**
     * Registra un retiro de una cuenta.
     *
     * @param request cuenta, importe y descripcion
     * @return el movimiento registrado, con estado 201
     */
    @PostMapping("/withdrawal")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionResponse> withdrawal(@Valid @RequestBody AccountTransactionRequest request) {
        return transactionService.withdrawal(request);
    }

    /**
     * Registra el pago de un credito o de una tarjeta de credito.
     *
     * @param request producto, importe y descripcion
     * @return el movimiento registrado, con estado 201
     */
    @PostMapping("/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionResponse> payment(@Valid @RequestBody PaymentRequest request) {
        return transactionService.payment(request);
    }

    /**
     * Registra un consumo con tarjeta de credito.
     *
     * @param request tarjeta, importe y descripcion
     * @return el movimiento registrado, con estado 201
     */
    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<TransactionResponse> charge(@Valid @RequestBody ChargeRequest request) {
        return transactionService.charge(request);
    }

    /**
     * Lista los movimientos, opcionalmente filtrados por producto.
     *
     * @param productId filtro opcional por producto
     * @return flujo de movimientos, con estado 200
     */
    @GetMapping
    public Flowable<TransactionResponse> findAll(@RequestParam(required = false) String productId) {
        return transactionService.findAll(productId);
    }

    /**
     * Busca un movimiento por su identificador.
     *
     * @param id identificador del movimiento
     * @return el movimiento encontrado, con estado 200
     */
    @GetMapping("/{id}")
    public Single<TransactionResponse> findById(@PathVariable String id) {
        return transactionService.findById(id);
    }

    /**
     * Corrige los datos de un movimiento.
     *
     * <p>Operacion administrativa: no recalcula saldos.</p>
     *
     * @param id      identificador del movimiento
     * @param request datos corregidos
     * @return el movimiento actualizado, con estado 200
     */
    @PutMapping("/{id}")
    public Single<TransactionResponse> update(@PathVariable String id,
                                              @Valid @RequestBody TransactionUpdateRequest request) {
        return transactionService.update(id, request);
    }

    /**
     * Elimina un movimiento.
     *
     * <p>Operacion administrativa: no recalcula saldos.</p>
     *
     * @param id identificador del movimiento
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return transactionService.delete(id);
    }
}
