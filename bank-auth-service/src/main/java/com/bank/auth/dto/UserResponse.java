package com.bank.auth.dto;

import com.bank.auth.model.Role;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usuario expuesto por la API (sin el hash de la contrasena).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /** Identificador. */
    private String id;

    /** Nombre de usuario. */
    private String username;

    /** Roles. */
    private List<Role> roles;

    /** Cliente del banco asociado. */
    private String customerId;

    /** Celular. */
    private String phoneNumber;

    /** Si puede iniciar sesion. */
    private boolean enabled;

    /** Fecha y hora de alta. */
    private LocalDateTime createdAt;
}
