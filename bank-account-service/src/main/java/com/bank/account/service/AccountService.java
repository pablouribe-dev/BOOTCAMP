package com.bank.account.service;

import com.bank.account.dto.AccountRequest;
import com.bank.account.dto.AccountResponse;
import com.bank.account.dto.AverageDailyBalanceResponse;
import com.bank.account.dto.BalanceOperationRequest;
import com.bank.account.dto.BalanceResponse;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.YearMonth;

/**
 * Reglas de negocio de las cuentas bancarias.
 */
public interface AccountService {

    /**
     * Abre una cuenta nueva aplicando las reglas del tipo de cuenta y del tipo
     * de cliente.
     *
     * @param request datos de apertura
     * @return la cuenta creada
     */
    Single<AccountResponse> create(AccountRequest request);

    /**
     * Lista las cuentas, opcionalmente filtradas por cliente o por numero de
     * cuenta. Si llegan ambos filtros, manda el numero de cuenta.
     *
     * @param customerId    identificador del cliente, o {@code null}
     * @param accountNumber numero de cuenta, o {@code null}
     * @return flujo de cuentas
     */
    Flowable<AccountResponse> findAll(String customerId, String accountNumber);

    /**
     * Calcula el promedio diario del saldo en un mes e informa si cumple el
     * minimo de la cuenta (solo VIP tienen minimo).
     *
     * @param id    identificador de la cuenta
     * @param month mes a calcular
     * @return promedio y cumplimiento del minimo
     */
    Single<AverageDailyBalanceResponse> getAverageDailyBalance(String id, YearMonth month);

    /**
     * Busca una cuenta por su identificador.
     *
     * @param id identificador de la cuenta
     * @return la cuenta encontrada
     */
    Single<AccountResponse> findById(String id);

    /**
     * Actualiza los datos editables de una cuenta.
     *
     * @param id      identificador de la cuenta
     * @param request datos nuevos
     * @return la cuenta actualizada
     */
    Single<AccountResponse> update(String id, AccountRequest request);

    /**
     * Elimina una cuenta.
     *
     * @param id identificador de la cuenta
     * @return completable que termina cuando la cuenta fue eliminada
     */
    Completable delete(String id);

    /**
     * Consulta el saldo disponible de una cuenta.
     *
     * @param id identificador de la cuenta
     * @return saldo de la cuenta
     */
    Single<BalanceResponse> getBalance(String id);

    /**
     * Aplica un deposito o un retiro sobre el saldo de la cuenta.
     *
     * <p>Operacion interna invocada por {@code bank-transaction-service}.</p>
     *
     * @param id      identificador de la cuenta
     * @param request operacion e importe
     * @return saldo resultante
     */
    Single<BalanceResponse> applyBalanceOperation(String id, BalanceOperationRequest request);
}
