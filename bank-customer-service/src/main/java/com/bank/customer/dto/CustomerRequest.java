package com.bank.customer.dto;

import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.model.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de entrada para crear o actualizar un cliente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequest {

    /** Tipo de cliente. Obligatorio. */
    @NotNull(message = "type is required")
    private CustomerType type;

    /** Tipo de documento de identidad. Obligatorio. */
    @NotNull(message = "documentType is required")
    private DocumentType documentType;

    /** Numero de documento. Obligatorio y unico. */
    @NotBlank(message = "documentNumber is required")
    @Size(max = 20, message = "documentNumber must not exceed 20 characters")
    private String documentNumber;

    /** Nombre completo o razon social. Obligatorio. */
    @NotBlank(message = "name is required")
    private String name;

    /** Correo electronico de contacto. */
    @Email(message = "email must be a valid address")
    private String email;

    /** Telefono de contacto. */
    private String phoneNumber;

    /** Direccion del cliente. */
    private String address;

    /** Perfil comercial. Si no se envia se asume STANDARD. */
    private CustomerProfile profile;
}
