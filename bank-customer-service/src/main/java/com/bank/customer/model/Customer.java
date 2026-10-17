package com.bank.customer.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Documento MongoDB que representa a un cliente del banco.
 *
 * <p>Se almacena en la coleccion {@code customers} de la base
 * {@code bank_customers}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customers")
public class Customer {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Tipo de cliente: personal o empresarial. */
    private CustomerType type;

    /** Tipo de documento de identidad. */
    private DocumentType documentType;

    /** Numero de documento. Es unico en todo el sistema. */
    @Indexed(unique = true)
    private String documentNumber;

    /** Nombre completo del titular o razon social de la empresa. */
    private String name;

    /** Correo electronico de contacto. */
    private String email;

    /** Telefono de contacto. */
    private String phoneNumber;

    /** Direccion del cliente. */
    private String address;

    /** Perfil comercial. Por defecto {@link CustomerProfile#STANDARD}. */
    @Builder.Default
    private CustomerProfile profile = CustomerProfile.STANDARD;

    /** Fecha y hora de registro del cliente. */
    private LocalDateTime createdAt;
}
