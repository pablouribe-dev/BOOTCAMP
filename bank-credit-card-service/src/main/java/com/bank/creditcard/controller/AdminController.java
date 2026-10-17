package com.bank.creditcard.controller;

import com.bank.creditcard.dto.OverdueScanResponse;
import com.bank.creditcard.service.OverdueScanService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de administracion. En el gateway se exponen bajo
 * {@code /admin/credit-cards/**} y exigen rol {@code ADMIN}.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OverdueScanService overdueScanService;

    /**
     * Ejecuta ya la deteccion diaria de deudas vencidas (util para pruebas).
     *
     * @return cantidad de deudas notificadas, con estado 200
     */
    @PostMapping("/overdue-debts/scan")
    public Single<OverdueScanResponse> scanOverdueDebts() {
        return overdueScanService.scan();
    }
}
