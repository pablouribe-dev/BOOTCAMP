package com.bank.account.dto;

import com.bank.account.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Titular o firmante autorizado enviado y devuelto por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolderDto {

    /** Tipo de documento de identidad. Obligatorio. */
    @NotNull(message = "documentType is required")
    private DocumentType documentType;

    /** Numero de documento. Obligatorio. */
    @NotBlank(message = "documentNumber is required")
    private String documentNumber;

    /** Nombre completo. Obligatorio. */
    @NotBlank(message = "fullName is required")
    private String fullName;
}
