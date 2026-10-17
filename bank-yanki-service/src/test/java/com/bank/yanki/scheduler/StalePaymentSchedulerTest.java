package com.bank.yanki.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.yanki.service.YankiPaymentService;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StalePaymentSchedulerTest {

    @Mock
    private YankiPaymentService yankiPaymentService;

    @InjectMocks
    private StalePaymentScheduler scheduler;

    @Test
    void runsTheExpirationAndSurvivesFailures() {
        when(yankiPaymentService.expireStalePayments()).thenReturn(Single.just(2L), Single.just(0L),
                Single.error(new IllegalStateException("mongo down")));

        scheduler.expireStalePayments();
        scheduler.expireStalePayments();
        scheduler.expireStalePayments();

        verify(yankiPaymentService, times(3)).expireStalePayments();
    }
}
