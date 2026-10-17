package com.bank.account.service;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Registro de saldos diarios y calculo del promedio diario mensual.
 */
public interface DailyBalanceService {

    /**
     * Guarda (upsert) el saldo de cierre de hoy para la cuenta.
     *
     * @param accountId identificador de la cuenta
     * @param balance   saldo resultante de la ultima operacion
     * @return completable que termina cuando se guardo
     */
    Completable recordClosingBalance(String accountId, BigDecimal balance);

    /**
     * Calcula el promedio diario del mes. Para cada dia se toma el ultimo
     * saldo registrado hasta ese dia; los dias sin movimientos arrastran el
     * saldo anterior. Solo se consideran los dias desde la apertura de la
     * cuenta y hasta hoy.
     *
     * @param accountId   identificador de la cuenta
     * @param openingDate dia de apertura de la cuenta
     * @param month       mes a calcular
     * @return promedio redondeado a 2 decimales
     */
    Single<BigDecimal> averageForMonth(String accountId, LocalDate openingDate, YearMonth month);

    /**
     * Elimina el historial de saldos de una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @return completable que termina cuando se elimino
     */
    Completable deleteHistory(String accountId);
}
