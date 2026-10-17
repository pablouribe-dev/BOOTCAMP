package com.bank.account.model;

import com.bank.account.event.AccountOperationResultPayload;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Resultado de una operacion de cuenta pedida por Kafka (coleccion
 * {@code processed_operations}).
 *
 * <p>Si la misma operacion llega otra vez, no se vuelve a aplicar: se
 * republica este resultado.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "processed_operations")
public class ProcessedOperation {

    /** Identificador de la operacion ({@code operationId}); unico. */
    @Id
    private String operationId;

    /** {@code ACCOUNT_OPERATION_COMPLETED} o {@code ACCOUNT_OPERATION_REJECTED}. */
    private String resultType;

    /** Resultado publicado. */
    private AccountOperationResultPayload result;

    /** Momento en que se proceso. */
    private Instant processedAt;
}
