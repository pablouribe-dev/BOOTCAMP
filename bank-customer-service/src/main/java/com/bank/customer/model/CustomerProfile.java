package com.bank.customer.model;

/**
 * Perfil comercial del cliente.
 *
 * <p>Cada perfil declara para que tipo de cliente es valido: {@link #VIP} solo
 * para personas naturales y {@link #PYME} solo para empresas.
 * {@link #STANDARD} sirve para ambos.</p>
 */
public enum CustomerProfile {

    /** Perfil por defecto, sin beneficios adicionales. Valido para cualquier tipo. */
    STANDARD(null),

    /** Cliente personal preferente. */
    VIP(CustomerType.PERSONAL),

    /** Pequena o mediana empresa. */
    PYME(CustomerType.BUSINESS);

    /** Tipo de cliente al que se restringe el perfil; {@code null} si no hay restriccion. */
    private final CustomerType requiredType;

    /**
     * Crea el perfil con su restriccion de tipo de cliente.
     *
     * @param requiredType tipo de cliente exigido, o {@code null} si admite cualquiera
     */
    CustomerProfile(CustomerType requiredType) {
        this.requiredType = requiredType;
    }

    /**
     * Indica si el perfil puede asignarse a un cliente del tipo dado.
     *
     * @param type tipo del cliente
     * @return {@code true} si la combinacion es valida
     */
    public boolean isAllowedFor(CustomerType type) {
        return requiredType == null || requiredType == type;
    }
}
