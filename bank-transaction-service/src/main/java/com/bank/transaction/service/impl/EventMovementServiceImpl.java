package com.bank.transaction.service.impl;

import com.bank.transaction.adapter.ReactorRxAdapter;
import com.bank.transaction.event.AccountOperationResultPayload;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionOrigin;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.EventMovementService;
import io.reactivex.rxjava3.core.Completable;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementacion del registro de movimientos originados por eventos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventMovementServiceImpl implements EventMovementService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable recordCompletedOperation(String eventId, AccountOperationResultPayload result) {
        Optional<TransactionOrigin> origin = parseOrigin(result.getOrigin());
        if (origin.isEmpty() || origin.get() == TransactionOrigin.API) {
            log.warn("Ignoring completed operation {} with unexpected origin {}",
                    result.getOperationId(), result.getOrigin());
            return Completable.complete();
        }
        return ReactorRxAdapter.monoToSingle(transactionRepository.existsByOperationId(result.getOperationId()))
                .flatMapCompletable(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("Movement of operation {} already registered: skipping", result.getOperationId());
                        return Completable.complete();
                    }
                    Transaction transaction = toTransaction(origin.get(), eventId, result);
                    return ReactorRxAdapter.monoToCompletable(transactionRepository.save(transaction))
                            .doOnComplete(() -> log.info("{} {} of {} registered on {} {} (operation {})",
                                    origin.get(), transaction.getTransactionType(), transaction.getAmount(),
                                    transaction.getProductType(), transaction.getProductId(),
                                    result.getOperationId()));
                });
    }

    /**
     * Arma el movimiento segun el origen.
     *
     * @param origin  origen de la operacion
     * @param eventId evento recibido
     * @param result  operacion completada
     * @return movimiento listo para guardar
     */
    private Transaction toTransaction(TransactionOrigin origin, String eventId, AccountOperationResultPayload result) {
        boolean debitCard = origin == TransactionOrigin.DEBIT_CARD;
        Transaction transaction = transactionMapper.toDocument(
                debitCard ? ProductType.DEBIT_CARD : ProductType.ACCOUNT,
                debitCard ? result.getReference() : result.getAccountId(),
                result.getCustomerId(),
                debitCard ? TransactionType.DEBIT_PAYMENT : TransactionType.valueOf(result.getOperation()),
                result.getAmount(),
                debitCard ? "Debit card payment" : "Yanki payment " + result.getReference());
        transaction.setOrigin(origin);
        transaction.setAccountId(result.getAccountId());
        transaction.setReference(result.getReference());
        transaction.setEventId(eventId);
        transaction.setOperationId(result.getOperationId());
        return transaction;
    }

    /**
     * Convierte el origen recibido sin fallar ante valores desconocidos.
     *
     * @param origin texto recibido
     * @return origen, o vacio si no se reconoce
     */
    private static Optional<TransactionOrigin> parseOrigin(String origin) {
        return Arrays.stream(TransactionOrigin.values())
                .filter(value -> value.name().equals(origin))
                .findFirst();
    }
}
