package com.bank.creditcard.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Copia local de una deuda vencida de un cliente (coleccion
 * {@code overdue_debts}), alimentada por {@code bank.debt.events}.
 *
 * <p>Permite aplicar la regla de adquisicion sin consultar por REST al
 * servicio dueno del credito o de la tarjeta. Es unica por
 * {@code productType + productId}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "overdue_debts")
@CompoundIndex(name = "product_unique", def = "{'productType': 1, 'productId': 1}", unique = true)
public class OverdueDebt {

    /** Identificador generado por MongoDB. */
    @Id
    private String id;

    /** Cliente deudor. */
    @Indexed
    private String customerId;

    /** Producto con deuda: {@code CREDIT} o {@code CREDIT_CARD}. */
    private String productType;

    /** Identificador del producto con deuda. */
    private String productId;

    /** Fecha de vencimiento incumplida. */
    private LocalDate dueDate;

    /** Importe vencido. */
    private BigDecimal dueAmount;

    /** Ultima actualizacion de esta copia. */
    private Instant updatedAt;
}
