package com.bank.yanki.scheduler;

import com.bank.yanki.service.YankiPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vence periodicamente los pagos Yanki que quedaron colgados en {@code PENDING}
 * (intervalo desde configuracion: {@code bank.yanki.stale-check-interval-ms}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StalePaymentScheduler {

    private final YankiPaymentService yankiPaymentService;

    /**
     * Ejecuta la revision. Un fallo se registra y se reintenta en la siguiente corrida.
     */
    @Scheduled(fixedDelayString = "${bank.yanki.stale-check-interval-ms}")
    public void expireStalePayments() {
        try {
            long expired = yankiPaymentService.expireStalePayments().blockingGet();
            if (expired > 0) {
                log.warn("{} stale Yanki payment(s) expired", expired);
            }
        } catch (RuntimeException ex) {
            log.error("Stale Yanki payment check failed: {}", ex.getMessage(), ex);
        }
    }
}
