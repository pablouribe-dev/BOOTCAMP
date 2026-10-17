package com.bank.customer.dto;

import com.bank.customer.model.CustomerProfile;
import com.bank.customer.model.CustomerType;
import com.bank.customer.model.DocumentType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representacion de un cliente devuelta por la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    /** Identificador del cliente. */
    private String id;

    /** Tipo de cliente. */
    private CustomerType type;

    /** Tipo de documento de identidad. */
    private DocumentType documentType;

    /** Numero de documento. */
    private String documentNumber;

    /** Nombre completo o razon social. */
    private String name;

    /** Correo electronico de contacto. */
    private String email;

    /** Telefono de contacto. */
    private String phoneNumber;

    /** Direccion del cliente. */
    private String address;

    /** Perfil comercial. */
    private CustomerProfile profile;

    /** Fecha y hora de registro. */
    private LocalDateTime createdAt;
}
