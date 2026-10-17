package com.bank.auth.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Usuario del sistema (coleccion {@code users} de la base {@code bank_auth}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    /** Identificador generado por MongoDB; viaja como {@code sub} en el token. */
    @Id
    private String id;

    /** Nombre de usuario, unico. */
    @Indexed(unique = true)
    private String username;

    /** Hash BCrypt de la contrasena. Nunca se devuelve. */
    private String passwordHash;

    /** Roles del usuario. */
    @Builder.Default
    private List<Role> roles = new ArrayList<>();

    /**
     * Cliente del banco asociado (opcional e informativo: no se valida porque
     * este servicio no llama por REST a {@code bank-customer-service}).
     */
    private String customerId;

    /** Celular (opcional, informativo; util para Yanki). */
    private String phoneNumber;

    /** Si puede iniciar sesion. */
    @Builder.Default
    private boolean enabled = true;

    /** Fecha y hora de alta. */
    private LocalDateTime createdAt;
}
