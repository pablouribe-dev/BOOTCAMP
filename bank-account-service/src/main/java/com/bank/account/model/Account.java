package com.bank.account.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa una cuenta bancaria.
 *
 * <p>Se almacena en la coleccion {@code accounts} de la base
 * {@code bank_accounts}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accounts")
public class Account {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Numero de cuenta generado por el servicio. Es unico. */
    @Indexed(unique = true)
    private String accountNumber;

    /** Tipo de cuenta. */
    private AccountType accountType;

    /** Identificador del cliente dueno de la cuenta. */
    private String customerId;

    /** Saldo disponible. */
    private BigDecimal balance;

    /** Comision de mantenimiento. Cero para ahorro y plazo fijo. */
    private BigDecimal maintenanceFee;

    /** Maximo de movimientos al mes. Solo aplica a ahorro y plazo fijo. */
    private Integer monthlyMovementLimit;

    /** Dia del mes (1-28) en que la cuenta a plazo fijo admite su movimiento. */
    private Integer movementDay;

    /** Titulares de la cuenta. */
    @Builder.Default
    private List<Holder> holders = new ArrayList<>();

    /** Firmantes autorizados de la cuenta. */
    @Builder.Default
    private List<Holder> authorizedSigners = new ArrayList<>();

    /** Perfil copiado del cliente al abrir la cuenta. */
    @Builder.Default
    private AccountProfile profile = AccountProfile.STANDARD;

    /** Monto con el que se abrio la cuenta; quedo como saldo inicial. */
    private BigDecimal openingAmount;

    /** Promedio diario minimo exigido en el mes. Solo cuentas VIP. */
    private BigDecimal minimumDailyAverage;

    /** Cantidad de depositos y retiros al mes sin comision. */
    private Integer freeTransactionLimit;

    /** Comision por cada deposito o retiro que exceda {@link #freeTransactionLimit}. */
    private BigDecimal transactionFee;

    /** Fecha y hora de apertura. */
    private LocalDateTime createdAt;
}
