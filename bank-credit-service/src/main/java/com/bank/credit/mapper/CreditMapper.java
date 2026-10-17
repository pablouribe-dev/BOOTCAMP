package com.bank.credit.mapper;

import com.bank.credit.dto.CreditBalanceResponse;
import com.bank.credit.dto.CreditRequest;
import com.bank.credit.dto.CreditResponse;
import com.bank.credit.model.Credit;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Conversiones entre el documento {@link Credit} y sus DTOs.
 */
@Component
public class CreditMapper {

    /**
     * Construye un credito nuevo a partir de la peticion.
     *
     * <p>El saldo pendiente arranca igual al importe otorgado.</p>
     *
     * @param request datos enviados por el cliente de la API
     * @return documento listo para persistir
     */
    public Credit toDocument(CreditRequest request) {
        return Credit.builder()
                .creditType(request.getCreditType())
                .customerId(request.getCustomerId())
                .grantedAmount(request.getGrantedAmount())
                .outstandingBalance(request.getGrantedAmount())
                .interestRate(request.getInterestRate())
                .dueDate(request.getDueDate())
                .dueAmount(request.getDueAmount())
                .overdueNotified(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Convierte el documento en la respuesta expuesta por la API.
     *
     * @param credit documento de credito
     * @return DTO de respuesta
     */
    public CreditResponse toResponse(Credit credit) {
        return CreditResponse.builder()
                .id(credit.getId())
                .creditType(credit.getCreditType())
                .customerId(credit.getCustomerId())
                .grantedAmount(credit.getGrantedAmount())
                .outstandingBalance(credit.getOutstandingBalance())
                .interestRate(credit.getInterestRate())
                .dueDate(credit.getDueDate())
                .dueAmount(credit.getDueAmount())
                .overdueNotified(Boolean.TRUE.equals(credit.getOverdueNotified()))
                .createdAt(credit.getCreatedAt())
                .build();
    }

    /**
     * Construye la respuesta de consulta de saldo pendiente.
     *
     * @param credit documento de credito
     * @return DTO con el saldo pendiente
     */
    public CreditBalanceResponse toBalanceResponse(Credit credit) {
        return CreditBalanceResponse.builder()
                .creditId(credit.getId())
                .creditType(credit.getCreditType())
                .grantedAmount(credit.getGrantedAmount())
                .outstandingBalance(credit.getOutstandingBalance())
                .build();
    }
}
