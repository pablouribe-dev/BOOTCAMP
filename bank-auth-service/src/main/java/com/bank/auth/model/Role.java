package com.bank.auth.model;

/**
 * Roles del sistema. El gateway los aplica por ruta y metodo HTTP.
 */
public enum Role {

    /** Administrador: todo, incluidos PUT, DELETE, {@code /auth/users/**} y {@code /admin/**}. */
    ADMIN,

    /** Usuario: GET y POST sobre los recursos de negocio. */
    USER
}
