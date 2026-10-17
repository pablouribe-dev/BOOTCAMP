package com.bank.auth.mapper;

import com.bank.auth.dto.UserResponse;
import com.bank.auth.model.User;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Conversion del documento {@link User} a su DTO de respuesta.
 */
@Component
public class UserMapper {

    /**
     * Convierte el usuario en la respuesta de la API, sin el hash de la
     * contrasena.
     *
     * @param user documento de usuario
     * @return DTO de respuesta
     */
    public UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(List.copyOf(user.getRoles()))
                .customerId(user.getCustomerId())
                .phoneNumber(user.getPhoneNumber())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
