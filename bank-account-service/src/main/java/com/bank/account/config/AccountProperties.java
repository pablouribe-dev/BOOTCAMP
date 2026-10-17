package com.bank.account.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de negocio de las cuentas, servidos por el Config Server bajo el
 * prefijo {@code bank.accounts}. Ningun valor de negocio esta escrito en el
 * codigo: los de aqui son solo respaldo si falta la propiedad.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.accounts")
public class AccountProperties {

    /** Valores por tipo de cuenta. */
    private Defaults defaults = new Defaults();

    /** Valores propios del perfil VIP. */
    private ProfileSettings vip = new ProfileSettings();

    /** Valores propios del perfil PYME. */
    private ProfileSettings pyme = new ProfileSettings();

    /**
     * Valores por defecto de cada tipo de cuenta.
     */
    @Getter
    @Setter
    public static class Defaults {

        /** Cuenta de ahorro. */
        private TypeSettings savings = new TypeSettings();

        /** Cuenta corriente. */
        private TypeSettings current = new TypeSettings();

        /** Cuenta a plazo fijo. */
        private TypeSettings fixedTerm = new TypeSettings();
    }

    /**
     * Parametros de un tipo de cuenta.
     */
    @Getter
    @Setter
    public static class TypeSettings {

        /** Monto minimo de apertura. */
        private BigDecimal minimumOpeningAmount = BigDecimal.ZERO;

        /** Depositos y retiros al mes sin comision. */
        private Integer freeTransactionLimit = 0;

        /** Comision por cada transaccion que exceda el maximo gratuito. */
        private BigDecimal transactionFee = BigDecimal.ZERO;

        /** Comision de mantenimiento (solo cuenta corriente). */
        private BigDecimal maintenanceFee = BigDecimal.ZERO;

        /** Limite duro de movimientos al mes (ahorro y plazo fijo); {@code null} sin limite. */
        private Integer monthlyMovementLimit;
    }

    /**
     * Parametros de un perfil de cliente.
     */
    @Getter
    @Setter
    public static class ProfileSettings {

        /**
         * Monto minimo de apertura del perfil; si es {@code null} se usa el del
         * tipo de cuenta.
         */
        private BigDecimal minimumOpeningAmount;

        /** Promedio diario minimo exigido en el mes (solo VIP). */
        private BigDecimal minimumDailyAverage;
    }
}
