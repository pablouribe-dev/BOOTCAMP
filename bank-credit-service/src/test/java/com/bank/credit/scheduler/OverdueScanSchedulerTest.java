package com.bank.credit.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.credit.dto.OverdueScanResponse;
import com.bank.credit.service.OverdueScanService;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OverdueScanSchedulerTest {

    @Mock
    private OverdueScanService overdueScanService;

    @InjectMocks
    private OverdueScanScheduler scheduler;

    @Test
    void runsTheScan() {
        when(overdueScanService.scan()).thenReturn(Single.just(OverdueScanResponse.builder().detected(1).build()));

        scheduler.scanOverdueDebts();

        verify(overdueScanService).scan();
    }

    @Test
    void aFailedRunDoesNotKillTheScheduler() {
        when(overdueScanService.scan()).thenReturn(Single.error(new IllegalStateException("broker down")));

        scheduler.scanOverdueDebts();

        verify(overdueScanService).scan();
    }
}
