package com.bank.transaction.service;

import com.bank.transaction.dto.GeneralReportResponse;
import com.bank.transaction.dto.ProductReportResponse;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.model.ProductType;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;

/**
 * Reportes sobre los movimientos.
 */
public interface ReportService {

    /**
     * Reporte completo de un producto: datos, saldo actual, movimientos del
     * intervalo y totales por tipo de movimiento y comisiones.
     *
     * @param productType tipo de producto
     * @param productId   identificador del producto
     * @param from        inicio del intervalo, inclusive
     * @param to          fin del intervalo, inclusive
     * @return reporte del producto
     */
    Single<ProductReportResponse> productReport(ProductType productType, String productId, LocalDate from,
                                                LocalDate to);

    /**
     * Reporte general: por cada tipo de producto, cuantos productos tuvieron
     * movimientos, cuantos movimientos hubo y sus totales.
     *
     * @param from inicio del intervalo, inclusive
     * @param to   fin del intervalo, inclusive
     * @return reporte general
     */
    Single<GeneralReportResponse> generalReport(LocalDate from, LocalDate to);

    /**
     * Ultimos 10 movimientos de un producto, del mas reciente al mas antiguo.
     *
     * @param productType tipo de producto (hoy solo tarjeta de credito)
     * @param productId   identificador del producto
     * @return hasta 10 movimientos
     */
    Flowable<TransactionResponse> lastMovements(ProductType productType, String productId);
}
