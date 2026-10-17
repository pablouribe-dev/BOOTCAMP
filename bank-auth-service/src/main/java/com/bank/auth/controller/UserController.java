package com.bank.auth.controller;

import com.bank.auth.dto.UserCreateRequest;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.dto.UserUpdateRequest;
import com.bank.auth.service.UserService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD de usuarios. El gateway exige rol {@code ADMIN} en {@code /auth/users/**}.
 */
@RestController
@RequestMapping("/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Crea un usuario con roles.
     *
     * @param request datos del usuario
     * @return el usuario creado, con estado 201
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Single<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    /**
     * Lista los usuarios.
     *
     * @return flujo de usuarios
     */
    @GetMapping
    public Flowable<UserResponse> findAll() {
        return userService.findAll();
    }

    /**
     * Busca un usuario por id.
     *
     * @param id identificador
     * @return el usuario
     */
    @GetMapping("/{id}")
    public Single<UserResponse> findById(@PathVariable String id) {
        return userService.findById(id);
    }

    /**
     * Actualiza un usuario.
     *
     * @param id      identificador
     * @param request datos nuevos
     * @return el usuario actualizado
     */
    @PutMapping("/{id}")
    public Single<UserResponse> update(@PathVariable String id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    /**
     * Elimina un usuario.
     *
     * @param id identificador
     * @return completable vacio, con estado 204
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Completable delete(@PathVariable String id) {
        return userService.delete(id);
    }
}
