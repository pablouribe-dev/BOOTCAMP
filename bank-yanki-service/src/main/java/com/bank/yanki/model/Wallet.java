package com.bank.yanki.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Monedero Yanki (coleccion {@code wallets}, base {@code bank_yanki}). No
 * requiere ser cliente del banco.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wallets")
public class Wallet {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Tipo de documento. */
    private DocumentType documentType;

    /** Numero de documento, unico. */
    @Indexed(unique = true)
    private String documentNumber;

    /** Celular, unico: identifica al monedero en los pagos. */
    @Indexed(unique = true)
    private String phoneNumber;

    /** IMEI del telefono. */
    private String imei;

    /** Correo. */
    private String email;

    /** Saldo propio (se usa solo si no hay tarjeta asociada). Inicia en 0. */
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** Tarjeta de debito asociada (opcional). */
    private String debitCardNumber;

    /** Fecha y hora de alta. */
    private LocalDateTime createdAt;

    /** Bloqueo optimista: dos pagos simultaneos no pisan el saldo. */
    @Version
    private Long version;
}
