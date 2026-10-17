package com.bank.transaction.service.impl;

import com.bank.transaction.adapter.ReactorRxAdapter;
import com.bank.transaction.client.AccountDto;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ServiceUnavailableException;
import com.bank.transaction.model.TransactionOrigin;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reglas que se aplican a cada movimiento sobre una cuenta bancaria.
 *
 * <ul>
 *   <li>Reglas duras de la Parte I: limite mensual de la cuenta de ahorro y
 *       movimiento unico del plazo fijo en su dia. Aplican tambien a las
 *       transferencias.</li>
 *   <li>Comision por exceso: solo depositos y retiros cuentan y pagan.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMovementRules {

    /** Movimientos que cuentan para el maximo sin comision y que la pagan. */
    static final Set<TransactionType> FEE_COUNTED_TYPES = EnumSet.of(TransactionType.DEPOSIT,
            TransactionType.WITHDRAWAL);

    /**
     * Parte III: origenes de movimientos recibidos por Kafka (tarjeta de debito y
     * Yanki). No cuentan para los maximos de movimientos ni para la comision.
     */
    static final Set<TransactionOrigin> EVENT_ORIGINS = EnumSet.of(TransactionOrigin.DEBIT_CARD,
            TransactionOrigin.YANKI);

    /** Movimientos permitidos al mes en un plazo fijo si la cuenta no informa su limite. */
    private static final int FIXED_TERM_DEFAULT_LIMIT = 1;

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    /**
     * Valida las restricciones de movimiento propias del tipo de cuenta.
     *
     * @param account cuenta devuelta por {@code bank-account-service}
     * @return completable que falla con {@link BusinessRuleException} si el
     *         movimiento no esta permitido
     */
    public Completable validateHardRules(AccountDto account) {
        if (account.getAccountType() == null) {
            return Completable.error(new ServiceUnavailableException(
                    "bank-account-service did not return the accountType of account " + account.getId()));
        }
        return switch (account.getAccountType()) {
            case CURRENT -> Completable.complete();
            case SAVINGS -> validateSavings(account);
            case FIXED_TERM -> validateFixedTerm(account);
        };
    }

    /**
     * Calcula la comision de un deposito o retiro: si los depositos y retiros
     * del mes ya alcanzan el maximo gratuito, se cobra {@code transactionFee}.
     *
     * @param account cuenta devuelta por {@code bank-account-service}
     * @return comision a cobrar; cero si no corresponde
     */
    public Single<BigDecimal> calculateFee(AccountDto account) {
        Integer freeLimit = account.getFreeTransactionLimit();
        BigDecimal fee = account.getTransactionFee();
        if (freeLimit == null || fee == null || fee.signum() == 0) {
            return Single.just(BigDecimal.ZERO);
        }
        LocalDateTime[] month = currentMonth();
        return ReactorRxAdapter.monoToSingle(transactionRepository
                        .countByProductIdAndTransactionTypeInAndOriginNotInAndCreatedAtBetween(account.getId(),
                                FEE_COUNTED_TYPES, EVENT_ORIGINS, month[0], month[1]))
                .map(count -> {
                    if (count >= freeLimit) {
                        log.info("Account {} already has {} deposits/withdrawals this month (free limit {}): fee {}",
                                account.getId(), count, freeLimit, fee);
                        return fee;
                    }
                    log.debug("Account {} has {} of {} free transactions used", account.getId(), count, freeLimit);
                    return BigDecimal.ZERO;
                });
    }

    /**
     * Regla de la cuenta de ahorro: movimientos del mes en curso menores que
     * su limite mensual.
     *
     * @param account cuenta de ahorro
     * @return completable que falla si ya alcanzo el limite
     */
    private Completable validateSavings(AccountDto account) {
        Integer limit = account.getMonthlyMovementLimit();
        if (limit == null) {
            log.warn("SAVINGS account {} has no monthlyMovementLimit set; the movement is allowed", account.getId());
            return Completable.complete();
        }
        return validateMovementCount(account.getId(), limit, "SAVINGS");
    }

    /**
     * Regla del plazo fijo: hoy debe ser su dia de movimiento y no puede haber
     * otro movimiento en el mes.
     *
     * @param account cuenta a plazo fijo
     * @return completable que falla si no es el dia pactado o ya hubo un movimiento
     */
    private Completable validateFixedTerm(AccountDto account) {
        Integer movementDay = account.getMovementDay();
        int today = LocalDate.now(clock).getDayOfMonth();
        if (movementDay == null || movementDay != today) {
            log.warn("FIXED_TERM account {} only accepts movements on day {}, today is {}",
                    account.getId(), movementDay, today);
            return Completable.error(new BusinessRuleException(
                    "A FIXED_TERM account only accepts movements on day " + movementDay + " of the month"));
        }
        int limit = account.getMonthlyMovementLimit() != null
                ? account.getMonthlyMovementLimit()
                : FIXED_TERM_DEFAULT_LIMIT;
        return validateMovementCount(account.getId(), limit, "FIXED_TERM");
    }

    /**
     * Comprueba que la cuenta no haya alcanzado su limite de movimientos del mes.
     *
     * @param accountId        identificador de la cuenta
     * @param limit            maximo de movimientos permitidos en el mes
     * @param accountTypeLabel tipo de cuenta, solo para los mensajes
     * @return completable que falla si ya alcanzo el limite
     */
    private Completable validateMovementCount(String accountId, int limit, String accountTypeLabel) {
        LocalDateTime[] month = currentMonth();
        return ReactorRxAdapter.monoToSingle(
                        transactionRepository.countByProductIdAndOriginNotInAndCreatedAtBetween(
                                accountId, EVENT_ORIGINS, month[0], month[1]))
                .flatMapCompletable(count -> {
                    if (count >= limit) {
                        log.warn("{} account {} already has {} movement(s) this month, the limit is {}",
                                accountTypeLabel, accountId, count, limit);
                        return Completable.error(new BusinessRuleException(
                                "The account reached its monthly movement limit of " + limit));
                    }
                    return Completable.complete();
                });
    }

    /**
     * Inicio y fin (inclusive) del mes en curso.
     *
     * @return arreglo {@code [inicio, fin]}
     */
    private LocalDateTime[] currentMonth() {
        LocalDateTime start = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
        return new LocalDateTime[] {start, start.plusMonths(1).minusNanos(1)};
    }
}
