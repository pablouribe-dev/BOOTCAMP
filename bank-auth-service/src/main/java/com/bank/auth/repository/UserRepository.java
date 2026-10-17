package com.bank.auth.repository;

import com.bank.auth.model.Role;
import com.bank.auth.model.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de usuarios.
 */
@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {

    /**
     * Busca un usuario por nombre.
     *
     * @param username nombre de usuario
     * @return el usuario, o vacio
     */
    Mono<User> findByUsername(String username);

    /**
     * Indica si el nombre de usuario ya esta en uso.
     *
     * @param username nombre de usuario
     * @return {@code true} si existe
     */
    Mono<Boolean> existsByUsername(String username);

    /**
     * Indica si existe al menos un usuario con el rol dado.
     *
     * @param role rol buscado
     * @return {@code true} si existe
     */
    Mono<Boolean> existsByRolesContaining(Role role);
}
