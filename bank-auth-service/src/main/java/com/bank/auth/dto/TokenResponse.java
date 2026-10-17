package com.bank.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del login: {@code { accessToken, tokenType: "Bearer", expiresIn }}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /** JWT firmado. */
    private String accessToken;

    /** Siempre {@code Bearer}. */
    private String tokenType;

    /** Segundos de validez del token. */
    private long expiresIn;
}
