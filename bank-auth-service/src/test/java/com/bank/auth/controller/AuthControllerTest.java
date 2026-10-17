package com.bank.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.auth.dto.LoginRequest;
import com.bank.auth.dto.RegisterRequest;
import com.bank.auth.dto.TokenResponse;
import com.bank.auth.dto.UserCreateRequest;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.dto.UserUpdateRequest;
import com.bank.auth.exception.InvalidCredentialsException;
import com.bank.auth.model.Role;
import com.bank.auth.service.AuthService;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest({AuthController.class, UserController.class})
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @Test
    void registerReturns201AndValidatesThePassword() {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(Single.just(UserResponse.builder().id("u1").roles(List.of(Role.USER)).build()));

        webTestClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"ana\",\"password\":\"Secret123\"}").exchange()
                .expectStatus().isCreated().expectBody().jsonPath("$.roles[0]").isEqualTo("USER")
                .jsonPath("$.passwordHash").doesNotExist();
        webTestClient.post().uri("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"ana\",\"password\":\"123\"}").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void loginReturnsTheTokenOrAGeneric401() {
        when(authService.login(any(LoginRequest.class))).thenReturn(Single.just(
                TokenResponse.builder().accessToken("jwt").tokenType("Bearer").expiresIn(3600).build()));
        webTestClient.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"ana\",\"password\":\"Secret123\"}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.tokenType").isEqualTo("Bearer");

        when(authService.login(any(LoginRequest.class))).thenReturn(Single.error(new InvalidCredentialsException()));
        webTestClient.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"ana\",\"password\":\"bad\"}").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid username or password");
    }

    @Test
    void userCrud() {
        UserResponse user = UserResponse.builder().id("u1").username("ana").roles(List.of(Role.ADMIN)).build();
        when(userService.create(any(UserCreateRequest.class))).thenReturn(Single.just(user));
        when(userService.findAll()).thenReturn(Flowable.just(user));
        when(userService.findById("u1")).thenReturn(Single.just(user));
        when(userService.update(eq("u1"), any(UserUpdateRequest.class))).thenReturn(Single.just(user));
        when(userService.delete("u1")).thenReturn(Completable.complete());

        webTestClient.post().uri("/auth/users").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"ana\",\"password\":\"Secret123\",\"roles\":[\"ADMIN\"]}").exchange()
                .expectStatus().isCreated();
        webTestClient.get().uri("/auth/users").exchange().expectStatus().isOk();
        webTestClient.get().uri("/auth/users/u1").exchange().expectStatus().isOk();
        webTestClient.put().uri("/auth/users/u1").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"roles\":[\"ADMIN\"]}").exchange().expectStatus().isOk();
        webTestClient.delete().uri("/auth/users/u1").exchange().expectStatus().isNoContent();
    }
}
