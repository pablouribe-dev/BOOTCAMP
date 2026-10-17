package com.bank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Credenciales de {@code POST /auth/login}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /** Nombre de usuario. */
    @NotBlank(message = "username is required")
    private String username;

    /** Contrasena. */
    @NotBlank(message = "password is required")
    private String password;
}
