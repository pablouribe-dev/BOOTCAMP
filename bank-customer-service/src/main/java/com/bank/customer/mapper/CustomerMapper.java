package com.bank.customer.mapper;

import com.bank.customer.dto.CustomerRequest;
import com.bank.customer.dto.CustomerResponse;
import com.bank.customer.model.Customer;
import com.bank.customer.model.CustomerProfile;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Conversiones entre el documento {@link Customer} y sus DTOs.
 */
@Component
public class CustomerMapper {

    /**
     * Construye un documento nuevo a partir de la peticion de creacion.
     *
     * @param request datos enviados por el cliente de la API
     * @return documento listo para persistir, con perfil y fecha de registro
     */
    public Customer toDocument(CustomerRequest request) {
        return Customer.builder()
                .type(request.getType())
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .name(request.getName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .profile(Optional.ofNullable(request.getProfile()).orElse(CustomerProfile.STANDARD))
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Copia los campos modificables de la peticion sobre un documento existente.
     *
     * <p>El identificador y la fecha de registro no se alteran.</p>
     *
     * @param existing documento recuperado de la base de datos
     * @param request  datos nuevos
     * @return el mismo documento con los valores actualizados
     */
    public Customer merge(Customer existing, CustomerRequest request) {
        existing.setType(request.getType());
        existing.setDocumentType(request.getDocumentType());
        existing.setDocumentNumber(request.getDocumentNumber());
        existing.setName(request.getName());
        existing.setEmail(request.getEmail());
        existing.setPhoneNumber(request.getPhoneNumber());
        existing.setAddress(request.getAddress());
        existing.setProfile(Optional.ofNullable(request.getProfile()).orElse(existing.getProfile()));
        return existing;
    }

    /**
     * Convierte el documento en la respuesta expuesta por la API.
     *
     * @param customer documento de cliente
     * @return DTO de respuesta
     */
    public CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .type(customer.getType())
                .documentType(customer.getDocumentType())
                .documentNumber(customer.getDocumentNumber())
                .name(customer.getName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .address(customer.getAddress())
                .profile(customer.getProfile())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
