package com.bank.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.account.exception.BusinessRuleException;
import com.bank.account.model.DailyBalance;
import com.bank.account.repository.DailyBalanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class DailyBalanceServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Mock
    private DailyBalanceRepository repository;

    private DailyBalanceServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new DailyBalanceServiceImpl(repository, clock);
    }

    private DailyBalance record(int day, String balance) {
        return DailyBalance.builder().accountId("a1").date(LocalDate.of(2026, 10, day))
                .closingBalance(new BigDecimal(balance)).build();
    }

    @Test
    void recordCreatesTheDayEntryWhenMissing() {
        when(repository.findByAccountIdAndDate("a1", TODAY)).thenReturn(Mono.empty());
        when(repository.save(any(DailyBalance.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.recordClosingBalance("a1", new BigDecimal("150")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        ArgumentCaptor<DailyBalance> saved = ArgumentCaptor.forClass(DailyBalance.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDate()).isEqualTo(TODAY);
        assertThat(saved.getValue().getClosingBalance()).isEqualByComparingTo("150");
    }

    @Test
    void recordOverwritesTheDayEntry() {
        DailyBalance existing = record(10, "100");
        existing.setId("d1");
        when(repository.findByAccountIdAndDate("a1", TODAY)).thenReturn(Mono.just(existing));
        when(repository.save(any(DailyBalance.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        service.recordClosingBalance("a1", new BigDecimal("80")).test().awaitDone(1, TimeUnit.SECONDS)
                .assertComplete();

        assertThat(existing.getClosingBalance()).isEqualByComparingTo("80");
        assertThat(existing.getId()).isEqualTo("d1");
    }

    @Test
    void averageCarriesTheLastBalanceToDaysWithoutMovements() {
        // Apertura el dia 1 con 1000, el dia 6 baja a 400. Hoy es 10.
        // Dias 1-5: 1000 (5 dias), dias 6-10: 400 (5 dias) -> promedio 700.
        when(repository.findByAccountIdAndDateLessThanEqualOrderByDateAsc("a1", TODAY))
                .thenReturn(Flux.just(record(1, "1000"), record(6, "400")));

        service.averageForMonth("a1", LocalDate.of(2026, 10, 1), YearMonth.of(2026, 10)).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertValue(average -> average.compareTo(new BigDecimal("700.00")) == 0);
    }

    @Test
    void averageStartsOnTheOpeningDateAndUsesPreviousMonthBalance() {
        // Mes anterior completo: solo un registro de septiembre arrastrado a todo el mes.
        when(repository.findByAccountIdAndDateLessThanEqualOrderByDateAsc("a1", LocalDate.of(2026, 9, 30)))
                .thenReturn(Flux.just(DailyBalance.builder().accountId("a1").date(LocalDate.of(2026, 9, 20))
                        .closingBalance(new BigDecimal("300")).build()));

        service.averageForMonth("a1", LocalDate.of(2026, 9, 20), YearMonth.of(2026, 9)).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertValue(average -> average.compareTo(new BigDecimal("300.00")) == 0);
    }

    @Test
    void daysBeforeAnyRecordCountAsZero() {
        when(repository.findByAccountIdAndDateLessThanEqualOrderByDateAsc("a1", TODAY))
                .thenReturn(Flux.just(record(6, "1000")));

        // Dias 1-5 sin registro (0) y 6-10 con 1000 -> 500.
        service.averageForMonth("a1", LocalDate.of(2026, 1, 1), YearMonth.of(2026, 10)).test()
                .awaitDone(1, TimeUnit.SECONDS)
                .assertValue(average -> average.compareTo(new BigDecimal("500.00")) == 0);
    }

    @Test
    void monthWithoutHistoryIsRejected() {
        service.averageForMonth("a1", LocalDate.of(2026, 10, 1), YearMonth.of(2026, 8)).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
        service.averageForMonth("a1", LocalDate.of(2026, 10, 1), YearMonth.of(2026, 12)).test()
                .awaitDone(1, TimeUnit.SECONDS).assertError(BusinessRuleException.class);
    }

    @Test
    void deleteHistory() {
        when(repository.deleteByAccountId("a1")).thenReturn(Mono.empty());

        service.deleteHistory("a1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(repository).deleteByAccountId("a1");
    }
}
