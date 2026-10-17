package com.bank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alta publica de un usuario ({@code POST /auth/register}); siempre con rol {@code USER}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /** Nombre de usuario. Obligatorio, de 3 a 50 caracteres. */
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must have between 3 and 50 characters")
    private String username;

    /** Contrasena. Obligatoria, minimo 8 caracteres. */
    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must have at least 8 characters")
    private String password;

    /** Cliente del banco asociado (opcional, informativo). */
    private String customerId;

    /** Celular (opcional). */
    private String phoneNumber;
}
