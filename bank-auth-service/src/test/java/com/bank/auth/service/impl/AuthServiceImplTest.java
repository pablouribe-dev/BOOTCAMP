package com.bank.auth.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.exception.InvalidCredentialsException;
import com.bank.auth.model.Role;
import com.bank.auth.model.User;
import com.bank.auth.repository.UserRepository;
import com.bank.auth.security.JwtTokenProvider;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userService, userRepository, passwordEncoder, jwtTokenProvider);
    }

    private User user(boolean enabled) {
        return User.builder().id("u1").username("ana").passwordHash(passwordEncoder.encode("Secret123"))
                .roles(List.of(Role.USER)).enabled(enabled).build();
    }

    @Test
    void registerAlwaysCreatesAUserWithRoleUser() {
        when(userService.createUser("ana", "Secret123", List.of(Role.USER), "c1", "999"))
                .thenReturn(Single.just(UserResponse.builder().id("u1").roles(List.of(Role.USER)).build()));

        service.register(RegisterRequest.builder().username("ana").password("Secret123").customerId("c1")
                        .phoneNumber("999").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertValue(user -> user.getRoles().equals(List.of(Role.USER)));
    }

    @Test
    void validCredentialsReturnABearerToken() {
        when(userRepository.findByUsername("ana")).thenReturn(Mono.just(user(true)));
        when(jwtTokenProvider.createToken(any())).thenReturn("jwt");
        when(jwtTokenProvider.expiresInSeconds()).thenReturn(3600L);

        service.login(LoginRequest.builder().username("ana").password("Secret123").build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertValue(token -> "jwt".equals(token.getAccessToken()) && "Bearer".equals(token.getTokenType())
                        && token.getExpiresIn() == 3600);
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledUserGetTheSameGeneric401() {
        when(userRepository.findByUsername("ana")).thenReturn(Mono.just(user(true)));
        when(userRepository.findByUsername("ghost")).thenReturn(Mono.empty());
        when(userRepository.findByUsername("off")).thenReturn(Mono.just(user(false)));

        service.login(LoginRequest.builder().username("ana").password("wrong").build())
                .test().awaitDone(1, TimeUnit.SECONDS)
                .assertError(error -> error instanceof InvalidCredentialsException
                        && "Invalid username or password".equals(error.getMessage()));
        service.login(LoginRequest.builder().username("ghost").password("Secret123").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(InvalidCredentialsException.class);
        service.login(LoginRequest.builder().username("off").password("Secret123").build())
                .test().awaitDone(1, TimeUnit.SECONDS).assertError(InvalidCredentialsException.class);
        verify(jwtTokenProvider, never()).createToken(any());
    }
}
