package com.bank.transaction.controller;

import com.bank.transaction.dto.GeneralReportResponse;
import com.bank.transaction.dto.ProductReportResponse;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.service.ReportService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de los reportes.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Reporte completo de un producto en un intervalo.
     *
     * @param productType tipo de producto
     * @param productId   identificador del producto
     * @param from        inicio del intervalo ({@code YYYY-MM-DD}), inclusive
     * @param to          fin del intervalo ({@code YYYY-MM-DD}), inclusive
     * @return reporte del producto, con estado 200
     */
    @GetMapping("/products/{productType}/{productId}")
    public Single<ProductReportResponse> productReport(
            @PathVariable ProductType productType,
            @PathVariable String productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.productReport(productType, productId, from, to);
    }

    /**
     * Reporte general agrupado por tipo de producto.
     *
     * @param from inicio del intervalo ({@code YYYY-MM-DD}), inclusive
     * @param to   fin del intervalo ({@code YYYY-MM-DD}), inclusive
     * @return reporte general, con estado 200
     */
    @GetMapping("/products")
    public Single<GeneralReportResponse> generalReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.generalReport(from, to);
    }

    /**
     * Ultimos 10 movimientos de un producto, del mas reciente al mas antiguo.
     *
     * @param productType tipo de producto (hoy solo {@code CREDIT_CARD})
     * @param productId   identificador del producto
     * @return hasta 10 movimientos, con estado 200
     */
    @GetMapping("/last-movements")
    public Flowable<TransactionResponse> lastMovements(@RequestParam ProductType productType,
                                                       @RequestParam String productId) {
        return reportService.lastMovements(productType, productId);
    }
}
