package com.bank.auth.dto;

import com.bank.auth.model.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Actualizacion de un usuario ({@code PUT /auth/users/{id}}). El nombre de usuario
 * no cambia; la contrasena solo si viene.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    /** Contrasena nueva (opcional), minimo 8 caracteres. */
    @Size(min = 8, max = 100, message = "password must have at least 8 characters")
    private String password;

    /** Roles. Al menos uno. */
    @NotEmpty(message = "roles must have at least one role")
    private List<Role> roles;

    /** Cliente del banco asociado. */
    private String customerId;

    /** Celular. */
    private String phoneNumber;

    /** Si puede iniciar sesion. */
    private Boolean enabled;
}
