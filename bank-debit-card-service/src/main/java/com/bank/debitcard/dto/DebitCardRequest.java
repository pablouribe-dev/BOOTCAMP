package com.bank.debitcard.dto;

import com.bank.debitcard.model.CardStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos para crear ({@code POST}) o actualizar ({@code PUT}) una tarjeta de
 * debito. En la actualizacion el cliente no cambia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitCardRequest {

    /** Cliente titular. Debe existir en la copia local de clientes. */
    @NotBlank(message = "customerId is required")
    private String customerId;

    /** Cuentas a asociar, en orden. Todas del mismo cliente. */
    @NotEmpty(message = "accountIds must have at least one account")
    private List<String> accountIds;

    /** Cuenta principal; debe estar en {@link #accountIds}. */
    @NotBlank(message = "mainAccountId is required")
    private String mainAccountId;

    /** Estado (solo en el PUT; opcional). */
    private CardStatus status;
}
