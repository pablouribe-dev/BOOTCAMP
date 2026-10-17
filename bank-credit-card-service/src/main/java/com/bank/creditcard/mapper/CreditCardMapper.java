package com.bank.creditcard.mapper;

import com.bank.creditcard.dto.CardBalanceResponse;
import com.bank.creditcard.dto.CreditCardRequest;
import com.bank.creditcard.dto.CreditCardResponse;
import com.bank.creditcard.model.CreditCard;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Conversiones entre el documento {@link CreditCard} y sus DTOs.
 */
@Component
public class CreditCardMapper {

    /**
     * Construye una tarjeta nueva a partir de la peticion.
     *
     * <p>El numero de tarjeta lo asigna la capa de servicio; el importe
     * consumido arranca en cero.</p>
     *
     * @param request datos enviados por el cliente de la API
     * @return documento parcialmente construido
     */
    public CreditCard toDocument(CreditCardRequest request) {
        return CreditCard.builder()
                .cardType(request.getCardType())
                .customerId(request.getCustomerId())
                .creditLimit(request.getCreditLimit())
                .usedAmount(BigDecimal.ZERO)
                .paymentDueDate(request.getPaymentDueDate())
                .dueAmount(request.getDueAmount())
                .overdueNotified(false)
                .build();
    }

    /**
     * Convierte el documento en la respuesta expuesta por la API.
     *
     * @param card documento de tarjeta
     * @return DTO de respuesta, con el saldo disponible calculado
     */
    public CreditCardResponse toResponse(CreditCard card) {
        return CreditCardResponse.builder()
                .id(card.getId())
                .cardNumber(card.getCardNumber())
                .cardType(card.getCardType())
                .customerId(card.getCustomerId())
                .creditLimit(card.getCreditLimit())
                .usedAmount(card.getUsedAmount())
                .availableBalance(availableBalance(card))
                .paymentDueDate(card.getPaymentDueDate())
                .dueAmount(card.getDueAmount())
                .overdueNotified(Boolean.TRUE.equals(card.getOverdueNotified()))
                .createdAt(card.getCreatedAt())
                .build();
    }

    /**
     * Construye la respuesta de consulta de saldo disponible.
     *
     * @param card documento de tarjeta
     * @return DTO con la linea, el consumo y el saldo disponible
     */
    public CardBalanceResponse toBalanceResponse(CreditCard card) {
        return CardBalanceResponse.builder()
                .cardId(card.getId())
                .cardNumber(card.getCardNumber())
                .creditLimit(card.getCreditLimit())
                .usedAmount(card.getUsedAmount())
                .availableBalance(availableBalance(card))
                .build();
    }

    /**
     * Calcula el saldo disponible de una tarjeta.
     *
     * <p>No se persiste: siempre se deriva de la linea y el consumo.</p>
     *
     * @param card documento de tarjeta
     * @return {@code creditLimit - usedAmount}
     */
    public BigDecimal availableBalance(CreditCard card) {
        return card.getCreditLimit().subtract(card.getUsedAmount());
    }
}
