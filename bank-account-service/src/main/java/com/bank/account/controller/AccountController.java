package com.bank.account.controller;

import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.AverageDailyBalanceResponse;
import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.dto.BalanceResponse;
import com.bank.account.service.AccountService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Endpoints REST de las cuentas bancarias.
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Abre una cuenta nueva.
     *
     * @param request datos de apertura
     * @return la cuenta creada, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return accountService.create(request);
    }

    /**
     * Lista las cuentas, opcionalmente filtradas por cliente o por numero de
     * cuenta.
     *
     * @param customerId    filtro opcional por cliente
     * @param accountNumber filtro opcional por numero de cuenta
     * @return flujo de cuentas, con estado 200
     */
    @GetMapping
    public Flowable<AccountResponse> findAll(@RequestParam(required = false) String customerId,
                                             @RequestParam(required = false) String accountNumber) {
        return accountService.findAll(customerId, accountNumber);
    }

    /**
     * Calcula el promedio diario del saldo en un mes.
     *
     * @param id    identificador de la cuenta
     * @param month mes en formato {@code YYYY-MM}
     * @return promedio y si cumple el minimo (null si la cuenta no tiene minimo)
     */
    @GetMapping("/{id}/average-daily-balance")
    public Single<AverageDailyBalanceResponse> getAverageDailyBalance(
            @PathVariable String id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return accountService.getAverageDailyBalance(id, month);
    }

    /**
     * Busca una cuenta por su identificador.
     *
     * @param id identificador de la cuenta
     * @return la cuenta encontrada, con estado 200
     */
    @GetMapping("/{id}")
    public Single<AccountResponse> findById(@PathVariable String id) {
        return accountService.findById(id);
    }

    /**
     * Actualiza los datos editables de una cuenta.
     *
     * @param id      identificador de la cuenta
     * @param request datos nuevos
     * @return la cuenta actualizada, con estado 200
     */
    @PutMapping("/{id}")
    public Single<AccountResponse> update(@PathVariable String id,
                                          @Valid @RequestBody AccountRequest request) {
        return accountService.update(id, request);
    }

    /**
     * Elimina una cuenta.
     *
     * @param id identificador de la cuenta
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return accountService.delete(id);
    }

    /**
     * Consulta el saldo disponible de una cuenta.
     *
     * @param id identificador de la cuenta
     * @return saldo de la cuenta, con estado 200
     */
    @GetMapping("/{id}/balance")
    public Single<BalanceResponse> getBalance(@PathVariable String id) {
        return accountService.getBalance(id);
    }

    /**
     * Aplica un deposito o un retiro sobre el saldo de la cuenta.
     *
     * <p>Endpoint interno: lo invoca {@code bank-transaction-service} y rechaza
     * el retiro si no hay fondos suficientes.</p>
     *
     * @param id      identificador de la cuenta
     * @param request operacion e importe
     * @return saldo resultante, con estado 200
     */
    @PostMapping("/{id}/balance-operations")
    public Single<BalanceResponse> applyBalanceOperation(@PathVariable String id,
                                                         @Valid @RequestBody BalanceOperationRequest request) {
        return accountService.applyBalanceOperation(id, request);
    }
}
