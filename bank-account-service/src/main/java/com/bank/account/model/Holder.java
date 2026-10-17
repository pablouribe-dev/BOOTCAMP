package com.bank.account.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persona embebida en una cuenta, como titular o como firmante autorizado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Holder {

    /** Tipo de documento de identidad. */
    private DocumentType documentType;

    /** Numero de documento. */
    private String documentNumber;

    /** Nombre completo. */
    private String fullName;
}
