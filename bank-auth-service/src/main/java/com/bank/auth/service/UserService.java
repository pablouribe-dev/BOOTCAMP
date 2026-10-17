package com.bank.auth.service;

import com.bank.auth.dto.UserCreateRequest;
import com.bank.auth.dto.UserResponse;
import com.bank.auth.dto.UserUpdateRequest;
import com.bank.auth.model.Role;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

/**
 * Administracion de usuarios (rol {@code ADMIN} en el gateway).
 */
public interface UserService {

    /**
     * Crea un usuario con los roles indicados.
     *
     * @param request datos del usuario
     * @return el usuario creado
     */
    Single<UserResponse> create(UserCreateRequest request);

    /**
     * Crea un usuario con los datos minimos y los roles dados (uso interno del
     * registro publico y del administrador inicial).
     *
     * @param username    nombre de usuario
     * @param rawPassword contrasena en claro (se guarda con BCrypt)
     * @param roles       roles
     * @param customerId  cliente asociado, o {@code null}
     * @param phoneNumber celular, o {@code null}
     * @return el usuario creado
     */
    Single<UserResponse> createUser(String username, String rawPassword, List<Role> roles, String customerId,
                                    String phoneNumber);

    /**
     * Lista los usuarios.
     *
     * @return flujo de usuarios
     */
    Flowable<UserResponse> findAll();

    /**
     * Busca un usuario por id.
     *
     * @param id identificador
     * @return el usuario
     */
    Single<UserResponse> findById(String id);

    /**
     * Actualiza roles, datos de contacto, estado y, si viene, la contrasena.
     *
     * @param id      identificador
     * @param request datos nuevos
     * @return el usuario actualizado
     */
    Single<UserResponse> update(String id, UserUpdateRequest request);

    /**
     * Elimina un usuario.
     *
     * @param id identificador
     * @return completable que termina cuando se elimino
     */
    Completable delete(String id);
}
