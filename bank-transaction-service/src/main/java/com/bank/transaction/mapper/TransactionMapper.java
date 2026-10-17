package com.bank.transaction.mapper;

import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.dto.TransactionUpdateRequest;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionOrigin;
import com.bank.transaction.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Conversiones entre el documento {@link Transaction} y sus DTOs.
 */
@Component
public class TransactionMapper {

    /**
     * Construye el documento del movimiento a registrar.
     *
     * @param productType     tipo de producto afectado
     * @param productId       identificador del producto afectado
     * @param customerId      identificador del cliente dueno del producto
     * @param transactionType tipo de movimiento
     * @param amount          importe del movimiento
     * @param description     descripcion libre
     * @return documento listo para persistir
     */
    public Transaction toDocument(ProductType productType,
                                  String productId,
                                  String customerId,
                                  TransactionType transactionType,
                                  BigDecimal amount,
                                  String description) {
        return Transaction.builder()
                .productType(productType)
                .productId(productId)
                .customerId(customerId)
                .transactionType(transactionType)
                .amount(amount)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Copia sobre un movimiento existente los campos corregibles.
     *
     * <p>No altera saldos ni el tipo de movimiento o de producto.</p>
     *
     * @param existing movimiento recuperado de la base de datos
     * @param request  datos corregidos
     * @return el mismo movimiento con los valores actualizados
     */
    public Transaction merge(Transaction existing, TransactionUpdateRequest request) {
        Optional.ofNullable(request.getAmount()).ifPresent(existing::setAmount);
        Optional.ofNullable(request.getDescription()).ifPresent(existing::setDescription);
        return existing;
    }

    /**
     * Convierte el documento en la respuesta expuesta por la API.
     *
     * @param transaction documento de movimiento
     * @return DTO de respuesta
     */
    public TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .productType(transaction.getProductType())
                .productId(transaction.getProductId())
                .customerId(transaction.getCustomerId())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .feeCharged(Optional.ofNullable(transaction.getFeeCharged()).orElse(BigDecimal.ZERO))
                .transferId(transaction.getTransferId())
                .transferType(transaction.getTransferType())
                .payerCustomerId(transaction.getPayerCustomerId())
                .thirdParty(transaction.getThirdParty())
                .origin(Optional.ofNullable(transaction.getOrigin()).orElse(TransactionOrigin.API))
                .accountId(transaction.getAccountId())
                .reference(transaction.getReference())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
