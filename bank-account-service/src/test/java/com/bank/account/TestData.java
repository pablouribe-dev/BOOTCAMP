package com.bank.account;

import com.bank.account.config.AccountProperties;
import java.math.BigDecimal;

/**
 * Datos compartidos por los tests: la misma configuracion de negocio que el
 * config repo.
 */
public final class TestData {

    private TestData() {
    }

    /**
     * Propiedades de cuentas equivalentes a {@code bank-account-service.yml}.
     *
     * @return propiedades de prueba
     */
    public static AccountProperties accountProperties() {
        AccountProperties properties = new AccountProperties();
        AccountProperties.TypeSettings savings = properties.getDefaults().getSavings();
        savings.setFreeTransactionLimit(5);
        savings.setTransactionFee(new BigDecimal("1.50"));
        savings.setMonthlyMovementLimit(20);
        AccountProperties.TypeSettings current = properties.getDefaults().getCurrent();
        current.setFreeTransactionLimit(10);
        current.setTransactionFee(new BigDecimal("1.50"));
        current.setMaintenanceFee(new BigDecimal("10.00"));
        AccountProperties.TypeSettings fixedTerm = properties.getDefaults().getFixedTerm();
        fixedTerm.setFreeTransactionLimit(1);
        fixedTerm.setTransactionFee(new BigDecimal("1.50"));
        fixedTerm.setMonthlyMovementLimit(1);
        properties.getVip().setMinimumOpeningAmount(new BigDecimal("500"));
        properties.getVip().setMinimumDailyAverage(new BigDecimal("1000"));
        return properties;
    }
}
