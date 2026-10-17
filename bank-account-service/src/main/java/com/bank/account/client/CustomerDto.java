package com.bank.account.client;

import com.bank.account.model.AccountProfile;
import com.bank.account.model.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subconjunto de la respuesta de {@code GET /customers/{id}} que necesita este
 * servicio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto {

    /** Identificador del cliente. */
    private String id;

    /** Tipo de cliente, del que dependen las reglas de apertura. */
    private CustomerType type;

    /** Tipo de documento, usado al construir el titular de una cuenta personal. */
    private DocumentType documentType;

    /** Numero de documento. */
    private String documentNumber;

    /** Nombre completo o razon social. */
    private String name;

    /** Perfil comercial; se copia a la cuenta al abrirla. */
    private AccountProfile profile;
}
