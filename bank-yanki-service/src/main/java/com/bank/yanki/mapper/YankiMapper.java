package com.bank.yanki.mapper;

import com.bank.yanki.dto.WalletRequest;
import com.bank.yanki.dto.WalletResponse;
import com.bank.yanki.dto.YankiPaymentResponse;
import com.bank.yanki.model.Wallet;
import com.bank.yanki.model.YankiPayment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Conversiones de monederos y pagos.
 */
@Component
public class YankiMapper {

    /**
     * Monedero nuevo con saldo cero.
     *
     * @param request datos del monedero
     * @return documento listo para guardar
     */
    public Wallet toDocument(WalletRequest request) {
        return Wallet.builder()
                .documentType(request.getDocumentType())
                .documentNumber(request.getDocumentNumber())
                .phoneNumber(request.getPhoneNumber())
                .imei(request.getImei())
                .email(request.getEmail())
                .balance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Copia los datos editables (no el saldo ni la tarjeta).
     *
     * @param wallet  monedero guardado
     * @param request datos nuevos
     * @return el mismo monedero actualizado
     */
    public Wallet merge(Wallet wallet, WalletRequest request) {
        wallet.setDocumentType(request.getDocumentType());
        wallet.setDocumentNumber(request.getDocumentNumber());
        wallet.setPhoneNumber(request.getPhoneNumber());
        wallet.setImei(request.getImei());
        wallet.setEmail(request.getEmail());
        return wallet;
    }

    /**
     * Respuesta de un monedero.
     *
     * @param wallet monedero
     * @return DTO
     */
    public WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .documentType(wallet.getDocumentType())
                .documentNumber(wallet.getDocumentNumber())
                .phoneNumber(wallet.getPhoneNumber())
                .imei(wallet.getImei())
                .email(wallet.getEmail())
                .balance(wallet.getBalance())
                .debitCardNumber(wallet.getDebitCardNumber())
                .createdAt(wallet.getCreatedAt())
                .build();
    }

    /**
     * Respuesta de un pago.
     *
     * @param payment pago
     * @return DTO
     */
    public YankiPaymentResponse toResponse(YankiPayment payment) {
        return YankiPaymentResponse.builder()
                .paymentId(payment.getId())
                .fromPhoneNumber(payment.getFromPhoneNumber())
                .toPhoneNumber(payment.getToPhoneNumber())
                .amount(payment.getAmount())
                .description(payment.getDescription())
                .status(payment.getStatus())
                .step(payment.getStep())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
