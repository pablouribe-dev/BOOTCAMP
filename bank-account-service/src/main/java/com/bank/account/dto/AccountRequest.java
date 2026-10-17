package com.bank.account.dto;

import com.bank.account.model.AccountType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de entrada para abrir o actualizar una cuenta bancaria.
 *
 * <p>En una actualizacion ({@code PUT}) solo se toman en cuenta
 * {@code maintenanceFee}, {@code monthlyMovementLimit}, {@code movementDay},
 * {@code holders} y {@code authorizedSigners}. El saldo nunca se edita por
 * aqui: cambia solo a traves de los movimientos.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRequest {

    /** Tipo de cuenta. Obligatorio. */
    @NotNull(message = "accountType is required")
    private AccountType accountType;

    /** Identificador del cliente dueno de la cuenta. Obligatorio. */
    @NotBlank(message = "customerId is required")
    private String customerId;

    /**
     * Monto de apertura; queda como saldo inicial. Si no se envia se asume cero.
     * Debe ser mayor o igual al minimo configurado para el tipo de cuenta y
     * perfil. Acepta tambien el nombre {@code initialBalance} de la Parte I.
     */
    @JsonAlias("initialBalance")
    @PositiveOrZero(message = "openingAmount must be zero or positive")
    private BigDecimal openingAmount;

    /** Comision de mantenimiento. Si no se envia se toma el valor configurado. */
    @PositiveOrZero(message = "maintenanceFee must be zero or positive")
    private BigDecimal maintenanceFee;

    /** Limite de movimientos mensuales. Si no se envia se toma el configurado. */
    @Positive(message = "monthlyMovementLimit must be positive")
    private Integer monthlyMovementLimit;

    /** Dia del mes del movimiento. Obligatorio para cuentas a plazo fijo. */
    @Min(value = 1, message = "movementDay must be between 1 and 28")
    @Max(value = 28, message = "movementDay must be between 1 and 28")
    private Integer movementDay;

    /** Titulares. Obligatorio al menos uno en cuentas empresariales. */
    @Valid
    private List<HolderDto> holders;

    /** Firmantes autorizados. Opcional. */
    @Valid
    private List<HolderDto> authorizedSigners;
}
