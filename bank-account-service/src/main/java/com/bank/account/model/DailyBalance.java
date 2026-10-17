package com.bank.account.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Saldo de cierre de una cuenta en un dia.
 *
 * <p>Se guarda en la coleccion {@code daily_balances}, con un unico registro
 * por cuenta y fecha. Cada operacion de saldo actualiza el registro del dia, y
 * con estos registros se calcula el promedio diario mensual de las cuentas
 * VIP.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "daily_balances")
@CompoundIndex(name = "account_date_unique", def = "{'accountId': 1, 'date': 1}", unique = true)
public class DailyBalance {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Cuenta a la que pertenece el saldo. */
    private String accountId;

    /** Dia del saldo. */
    private LocalDate date;

    /** Saldo de la cuenta al final de la ultima operacion del dia. */
    private BigDecimal closingBalance;
}
