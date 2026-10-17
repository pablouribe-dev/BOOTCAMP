package com.bank.account.service.impl;

import com.bank.account.adapter.ReactorRxAdapter;
import com.bank.account.exception.BusinessRuleException;
import com.bank.account.model.DailyBalance;
import com.bank.account.repository.DailyBalanceRepository;
import com.bank.account.service.DailyBalanceService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion del registro de saldos diarios y del promedio mensual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBalanceServiceImpl implements DailyBalanceService {

    private final DailyBalanceRepository dailyBalanceRepository;
    private final Clock clock;

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable recordClosingBalance(String accountId, BigDecimal balance) {
        LocalDate today = LocalDate.now(clock);
        return ReactorRxAdapter.monoToMaybe(dailyBalanceRepository.findByAccountIdAndDate(accountId, today))
                .defaultIfEmpty(DailyBalance.builder().accountId(accountId).date(today).build())
                .map(record -> {
                    record.setClosingBalance(balance);
                    return record;
                })
                .flatMapCompletable(record -> ReactorRxAdapter.monoToCompletable(dailyBalanceRepository.save(record)))
                .doOnComplete(() -> log.debug("Closing balance of account {} on {} set to {}",
                        accountId, today, balance));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<BigDecimal> averageForMonth(String accountId, LocalDate openingDate, YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = max(month.atDay(1), openingDate);
        LocalDate lastDay = min(month.atEndOfMonth(), today);
        if (firstDay.isAfter(lastDay)) {
            log.warn("Account {} has no balance history in {}", accountId, month);
            return Single.error(new BusinessRuleException(
                    "Account " + accountId + " has no balance history in " + month));
        }
        return ReactorRxAdapter.fluxToFlowable(
                        dailyBalanceRepository.findByAccountIdAndDateLessThanEqualOrderByDateAsc(accountId, lastDay))
                .toList()
                .map(records -> average(records, firstDay, lastDay));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable deleteHistory(String accountId) {
        return ReactorRxAdapter.monoToCompletable(dailyBalanceRepository.deleteByAccountId(accountId));
    }

    /**
     * Promedia, con streams, el saldo de cada dia del intervalo arrastrando el
     * ultimo saldo conocido a los dias sin registro.
     *
     * @param records  saldos registrados hasta el ultimo dia, ordenados por fecha
     * @param firstDay primer dia del intervalo
     * @param lastDay  ultimo dia del intervalo
     * @return promedio redondeado a 2 decimales
     */
    private BigDecimal average(List<DailyBalance> records, LocalDate firstDay, LocalDate lastDay) {
        TreeMap<LocalDate, BigDecimal> balanceByDate = records.stream()
                .collect(Collectors.toMap(DailyBalance::getDate, DailyBalance::getClosingBalance,
                        (earlier, later) -> later, TreeMap::new));
        List<BigDecimal> dailyBalances = Stream.iterate(firstDay, day -> !day.isAfter(lastDay), day -> day.plusDays(1))
                .map(day -> Optional.ofNullable(balanceByDate.floorEntry(day))
                        .map(Map.Entry::getValue)
                        .orElse(BigDecimal.ZERO))
                .toList();
        BigDecimal total = dailyBalances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(dailyBalances.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * La mas reciente de dos fechas.
     *
     * @param first  primera fecha
     * @param second segunda fecha
     * @return la mayor
     */
    private static LocalDate max(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    /**
     * La mas antigua de dos fechas.
     *
     * @param first  primera fecha
     * @param second segunda fecha
     * @return la menor
     */
    private static LocalDate min(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
