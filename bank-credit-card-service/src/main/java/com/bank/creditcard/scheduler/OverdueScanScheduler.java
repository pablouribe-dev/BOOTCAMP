package com.bank.creditcard.scheduler;

import com.bank.creditcard.service.OverdueScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proceso diario que detecta deudas vencidas. El cron viene del config repo
 * ({@code bank.debts.overdue-scan-cron}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueScanScheduler {

    private final OverdueScanService overdueScanService;

    /**
     * Ejecuta la busqueda. Corre en el hilo del scheduler, asi que puede
     * esperar el resultado; un fallo se registra y se reintenta en la
     * siguiente corrida.
     */
    @Scheduled(cron = "${bank.debts.overdue-scan-cron}")
    public void scanOverdueDebts() {
        try {
            overdueScanService.scan().blockingGet();
        } catch (RuntimeException ex) {
            log.error("Overdue debt scan failed: {}", ex.getMessage(), ex);
        }
    }
}
