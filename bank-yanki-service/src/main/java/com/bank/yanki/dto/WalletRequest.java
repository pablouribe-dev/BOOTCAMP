package com.bank.yanki.dto;

import com.bank.yanki.model.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos para crear o actualizar un monedero Yanki.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletRequest {

    /** Tipo de documento. */
    @NotNull(message = "documentType is required")
    private DocumentType documentType;

    /** Numero de documento. */
    @NotBlank(message = "documentNumber is required")
    private String documentNumber;

    /** Celular, 9 digitos. */
    @NotBlank(message = "phoneNumber is required")
    @Pattern(regexp = "\\d{9}", message = "phoneNumber must have 9 digits")
    private String phoneNumber;

    /** IMEI del telefono. */
    @NotBlank(message = "imei is required")
    private String imei;

    /** Correo. */
    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;
}
