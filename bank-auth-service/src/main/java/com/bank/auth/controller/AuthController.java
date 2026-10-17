package com.bank.auth.controller;

import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.TokenResponse;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.service.AuthService;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos: registro y login.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registra un usuario con rol {@code USER}.
     *
     * @param request datos del usuario
     * @return el usuario creado, con estado 201
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Single<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Inicia sesion.
     *
     * @param request usuario y contrasena
     * @return token de acceso, con estado 200 (401 si las credenciales no son validas)
     */
    @PostMapping("/login")
    public Single<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
