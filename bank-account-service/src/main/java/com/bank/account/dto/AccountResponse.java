package com.bank.account.dto;

import com.bank.account.model.AccountProfile;
import com.bank.account.model.AccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representacion de una cuenta devuelta por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    /** Identificador de la cuenta. */
    private String id;

    /** Numero de cuenta. */
    private String accountNumber;

    /** Tipo de cuenta. */
    private AccountType accountType;

    /** Identificador del cliente dueno. */
    private String customerId;

    /** Saldo disponible. */
    private BigDecimal balance;

    /** Comision de mantenimiento. */
    private BigDecimal maintenanceFee;

    /** Limite de movimientos mensuales, si aplica. */
    private Integer monthlyMovementLimit;

    /** Dia del mes del movimiento, si aplica. */
    private Integer movementDay;

    /** Titulares de la cuenta. */
    private List<HolderDto> holders;

    /** Firmantes autorizados. */
    private List<HolderDto> authorizedSigners;

    /** Perfil de la cuenta, copiado del cliente. */
    private AccountProfile profile;

    /** Monto con el que se abrio la cuenta. */
    private BigDecimal openingAmount;

    /** Promedio diario minimo exigido, solo en cuentas VIP. */
    private BigDecimal minimumDailyAverage;

    /** Depositos y retiros al mes sin comision. */
    private Integer freeTransactionLimit;

    /** Comision por cada deposito o retiro que exceda el maximo gratuito. */
    private BigDecimal transactionFee;

    /** Fecha y hora de apertura. */
    private LocalDateTime createdAt;
}
