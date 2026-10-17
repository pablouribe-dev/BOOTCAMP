package com.bank.credit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.credit.event.CreditDebtEventPublisher;
import com.bank.credit.model.Credit;
import com.bank.credit.repository.CreditRepository;
import io.reactivex.rxjava3.core.Completable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OverdueScanServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock
    private CreditRepository creditRepository;

    @Mock
    private CreditDebtEventPublisher creditDebtEventPublisher;

    private OverdueScanServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
        service = new OverdueScanServiceImpl(creditRepository, creditDebtEventPublisher, clock);
    }

    private Credit credit(String id, LocalDate dueDate, String dueAmount) {
        return Credit.builder().id(id).customerId("c1").dueDate(dueDate)
                .dueAmount(dueAmount == null ? null : new BigDecimal(dueAmount)).build();
    }

    @Test
    void publishesDetectedOnlyForPositiveOverdueAmountsAndMarksThem() {
        Credit overdue = credit("k1", TODAY.minusDays(5), "150");
        Credit nothingDue = credit("k2", TODAY.minusDays(5), "0");
        when(creditRepository.findByDueDateBeforeAndOverdueNotifiedNot(TODAY, true))
                .thenReturn(Flux.just(overdue, nothingDue));
        when(creditDebtEventPublisher.publishDetected(overdue)).thenReturn(Completable.complete());
        when(creditRepository.save(any(Credit.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.scan().test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(response -> response.getDetected() == 1 && "CREDIT".equals(response.getProductType()));
        assertThat(overdue.getOverdueNotified()).isTrue();
        verify(creditDebtEventPublisher, never()).publishDetected(nothingDue);
    }

    @Test
    void kafkaFailureLeavesTheCreditUnmarkedForTheNextRun() {
        Credit overdue = credit("k1", TODAY.minusDays(5), "150");
        when(creditRepository.findByDueDateBeforeAndOverdueNotifiedNot(TODAY, true)).thenReturn(Flux.just(overdue));
        when(creditDebtEventPublisher.publishDetected(overdue))
                .thenReturn(Completable.error(new IllegalStateException("broker down")));

        service.scan().test().awaitDone(1, TimeUnit.SECONDS).assertError(IllegalStateException.class);
        verify(creditRepository, never()).save(any());
    }

    @Test
    void overdueRequiresAPositiveAmountAndAPastDueDate() {
        assertThat(OverdueScanServiceImpl.isOverdue(credit("k", TODAY.minusDays(1), "1"), TODAY)).isTrue();
        assertThat(OverdueScanServiceImpl.isOverdue(credit("k", TODAY, "1"), TODAY)).isFalse();
        assertThat(OverdueScanServiceImpl.isOverdue(credit("k", TODAY.minusDays(1), "0"), TODAY)).isFalse();
        assertThat(OverdueScanServiceImpl.isOverdue(credit("k", null, "1"), TODAY)).isFalse();
        assertThat(OverdueScanServiceImpl.isOverdue(credit("k", TODAY.minusDays(1), null), TODAY)).isFalse();
    }
}
