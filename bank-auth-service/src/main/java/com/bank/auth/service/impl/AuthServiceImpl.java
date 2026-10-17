package com.bank.auth.service.impl;

import com.bank.auth.adapter.ReactorRxAdapter;
import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.TokenResponse;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.exception.InvalidCredentialsException;
import com.bank.auth.model.Role;
import com.bank.auth.repository.UserRepository;
import com.bank.auth.security.JwtTokenProvider;
import com.bank.auth.service.AuthService;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Implementacion del registro y el login.
 *
 * <p>El login responde siempre el mismo 401 generico, sin revelar si fallo el
 * usuario, la contrasena o si la cuenta esta deshabilitada.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<UserResponse> register(RegisterRequest request) {
        return userService.createUser(request.getUsername(), request.getPassword(), List.of(Role.USER),
                request.getCustomerId(), request.getPhoneNumber());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TokenResponse> login(LoginRequest request) {
        return ReactorRxAdapter.monoToMaybe(userRepository.findByUsername(request.getUsername()))
                .filter(user -> user.isEnabled() && passwordEncoder.matches(request.getPassword(),
                        user.getPasswordHash()))
                .switchIfEmpty(Single.error(InvalidCredentialsException::new))
                .map(user -> TokenResponse.builder()
                        .accessToken(jwtTokenProvider.createToken(user))
                        .tokenType(TOKEN_TYPE)
                        .expiresIn(jwtTokenProvider.expiresInSeconds())
                        .build())
                .doOnSuccess(token -> log.info("User {} logged in", request.getUsername()));
    }
}
