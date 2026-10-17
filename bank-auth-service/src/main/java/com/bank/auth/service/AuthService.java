package com.bank.auth.service;

import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.TokenResponse;
import com.bank.auth.dto.UserResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Registro publico e inicio de sesion.
 */
public interface AuthService {

    /**
     * Registra un usuario con rol {@code USER}.
     *
     * @param request datos del usuario
     * @return el usuario creado
     */
    Single<UserResponse> register(RegisterRequest request);

    /**
     * Valida las credenciales y emite un JWT.
     *
     * @param request usuario y contrasena
     * @return token de acceso; falla con 401 si las credenciales no son validas
     */
    Single<TokenResponse> login(LoginRequest request);
}
