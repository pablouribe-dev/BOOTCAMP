package com.bank.auth.dto;

import com.bank.auth.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alta de un usuario por un administrador ({@code POST /auth/users}), con roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    /** Nombre de usuario. Obligatorio, de 3 a 50 caracteres. */
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must have between 3 and 50 characters")
    private String username;

    /** Contrasena. Obligatoria, minimo 8 caracteres. */
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must have at least 8 characters")
    private String password;

    /** Roles. Al menos uno. */
    @NotEmpty(message = "roles must have at least one role")
    private List<Role> roles;

    /** Cliente del banco asociado (opcional). */
    private String customerId;

    /** Celular (opcional). */
    private String phoneNumber;

    /** Si puede iniciar sesion ({@code true} por defecto). */
    private Boolean enabled;
}
