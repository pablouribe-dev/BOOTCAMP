package com.bank.yanki.dto;

import com.bank.yanki.model.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Monedero expuesto por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    /** Identificador. */
    private String id;

    /** Tipo de documento. */
    private DocumentType documentType;

    /** Numero de documento. */
    private String documentNumber;

    /** Celular. */
    private String phoneNumber;

    /** IMEI. */
    private String imei;

    /** Correo. */
    private String email;

    /** Saldo propio (sin uso mientras haya tarjeta asociada). */
    private BigDecimal balance;

    /** Tarjeta de debito asociada. */
    private String debitCardNumber;

    /** Fecha y hora de alta. */
    private LocalDateTime createdAt;
}
