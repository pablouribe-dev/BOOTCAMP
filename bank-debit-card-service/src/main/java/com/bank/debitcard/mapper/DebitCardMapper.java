package com.bank.debitcard.mapper;

import com.bank.debitcard.dto.DebitCardResponse;
import com.bank.debitcard.dto.DebitPaymentResponse;
import com.bank.debitcard.model.DebitCard;
import com.bank.debitcard.model.DebitPayment;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Conversiones de tarjetas y pagos a sus DTOs.
 */
@Component
public class DebitCardMapper {

    /**
     * Convierte una tarjeta en su respuesta.
     *
     * @param card tarjeta
     * @return DTO de respuesta
     */
    public DebitCardResponse toResponse(DebitCard card) {
        return DebitCardResponse.builder()
                .id(card.getId())
                .cardNumber(card.getCardNumber())
                .customerId(card.getCustomerId())
                .accountIds(List.copyOf(card.getAccountIds()))
                .mainAccountId(card.getMainAccountId())
                .status(card.getStatus())
                .createdAt(card.getCreatedAt())
                .build();
    }

    /**
     * Convierte un pago en su respuesta.
     *
     * @param payment pago
     * @return DTO de respuesta
     */
    public DebitPaymentResponse toResponse(DebitPayment payment) {
        return DebitPaymentResponse.builder()
                .paymentId(payment.getId())
                .cardId(payment.getCardId())
                .amount(payment.getAmount())
                .description(payment.getDescription())
                .status(payment.getStatus())
                .attemptedAccountIds(List.copyOf(payment.getAttemptedAccountIds()))
                .chargedAccountId(payment.getChargedAccountId())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
