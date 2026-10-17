package com.bank.transaction.service.impl;

import com.bank.transaction.adapter.ReactorRxAdapter;
import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.AccountDto;
import com.bank.transaction.client.AccountOperationRequest;
import com.bank.transaction.client.AccountOperationType;
import com.bank.transaction.dto.TransferRequest;
import com.bank.transaction.dto.TransferResponse;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.model.TransferType;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransferService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Implementacion de las transferencias con compensacion manual por pasos.
 *
 * <ol>
 *   <li>Origen y destino deben existir y ser distintos.</li>
 *   <li>Se aplican las reglas duras a ambas cuentas (ahorro y plazo fijo).</li>
 *   <li>Retiro del origen, sin comision. Si no hay fondos: 409.</li>
 *   <li>Deposito en el destino. Si falla, se devuelve el dinero al origen.</li>
 *   <li>Si todo sale bien, se guardan TRANSFER_OUT y TRANSFER_IN con el mismo
 *       {@code transferId}.</li>
 * </ol>
 *
 * <p>Las transferencias no cobran comision ni cuentan para el maximo de
 * transacciones gratis.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountClient accountClient;
    private final AccountMovementRules accountMovementRules;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TransferResponse> transfer(TransferRequest request) {
        String transferId = UUID.randomUUID().toString();
        log.info("Transfer {}: {} from account id={} to account number={}", transferId, request.getAmount(),
                request.getSourceAccountId(), request.getDestinationAccountNumber());
        return Single.zip(accountClient.findById(request.getSourceAccountId()),
                        accountClient.findByAccountNumber(request.getDestinationAccountNumber()),
                        AccountPair::new)
                .flatMap(pair -> validate(pair)
                        .andThen(Completable.defer(() -> moveMoney(transferId, pair, request.getAmount())))
                        .andThen(Single.defer(() -> saveMovements(transferId, pair, request))));
    }

    /**
     * Valida que sean cuentas distintas y aplica las reglas duras a ambas.
     *
     * @param pair cuenta origen y destino
     * @return completable que falla si la transferencia no esta permitida
     */
    private Completable validate(AccountPair pair) {
        if (pair.source().getId().equals(pair.destination().getId())) {
            log.warn("Transfer rejected: source and destination are the same account {}", pair.source().getId());
            return Completable.error(new BusinessRuleException("Source and destination accounts must be different"));
        }
        return accountMovementRules.validateHardRules(pair.source())
                .andThen(accountMovementRules.validateHardRules(pair.destination()));
    }

    /**
     * Retira del origen y deposita en el destino; si el deposito falla, compensa.
     *
     * @param transferId identificador de la transferencia
     * @param pair       cuenta origen y destino
     * @param amount     importe
     * @return completable que termina cuando ambos saldos se movieron
     */
    private Completable moveMoney(String transferId, AccountPair pair, BigDecimal amount) {
        String sourceId = pair.source().getId();
        String destinationId = pair.destination().getId();
        return accountClient.applyBalanceOperation(sourceId, operation(AccountOperationType.WITHDRAWAL, amount))
                .andThen(Completable.defer(() -> accountClient
                        .applyBalanceOperation(destinationId, operation(AccountOperationType.DEPOSIT, amount))
                        .onErrorResumeNext(depositError ->
                                compensate(transferId, sourceId, destinationId, amount, depositError))));
    }

    /**
     * Devuelve el dinero al origen tras un fallo del deposito en el destino.
     *
     * <p>Siempre termina en error (el del deposito), para que no se guarde
     * ningun movimiento. Si la compensacion tambien falla, deja un registro
     * {@code ERROR} con los ids involucrados para revision manual.</p>
     *
     * @param transferId    identificador de la transferencia
     * @param sourceId      cuenta origen
     * @param destinationId cuenta destino
     * @param amount        importe
     * @param depositError  error del deposito en el destino
     * @return completable que falla con el error del deposito
     */
    private Completable compensate(String transferId, String sourceId, String destinationId, BigDecimal amount,
                                   Throwable depositError) {
        log.warn("Transfer {}: deposit into account {} failed ({}); returning {} to account {}",
                transferId, destinationId, depositError.getMessage(), amount, sourceId);
        return accountClient.applyBalanceOperation(sourceId, operation(AccountOperationType.DEPOSIT, amount))
                .doOnComplete(() -> log.info("Transfer {}: compensation completed on account {}", transferId, sourceId))
                .onErrorResumeNext(compensationError -> {
                    log.error("Transfer {}: COMPENSATION FAILED. Manual review required: sourceAccountId={}, "
                                    + "destinationAccountId={}, amount={}, depositError={}, compensationError={}",
                            transferId, sourceId, destinationId, amount, depositError.getMessage(),
                            compensationError.getMessage());
                    return Completable.complete();
                })
                .andThen(Completable.error(depositError));
    }

    /**
     * Guarda los dos movimientos de la transferencia.
     *
     * @param transferId identificador comun
     * @param pair       cuenta origen y destino
     * @param request    datos de la transferencia
     * @return respuesta con ambos movimientos
     */
    private Single<TransferResponse> saveMovements(String transferId, AccountPair pair, TransferRequest request) {
        TransferType transferType = pair.source().getCustomerId().equals(pair.destination().getCustomerId())
                ? TransferType.OWN
                : TransferType.THIRD_PARTY;
        Transaction outgoing = movement(transferId, transferType, pair.source(), TransactionType.TRANSFER_OUT, request);
        Transaction incoming = movement(transferId, transferType, pair.destination(), TransactionType.TRANSFER_IN,
                request);
        return ReactorRxAdapter.fluxToFlowable(transactionRepository.saveAll(Flux.just(outgoing, incoming)))
                .toList()
                .map(saved -> response(transferId, transferType, request.getAmount(), saved))
                .doOnSuccess(response -> log.info("Transfer {} ({}) of {} registered", transferId, transferType,
                        request.getAmount()));
    }

    /**
     * Construye uno de los movimientos de la transferencia.
     *
     * @param transferId   identificador comun
     * @param transferType propia o a terceros
     * @param account      cuenta afectada
     * @param type         TRANSFER_OUT o TRANSFER_IN
     * @param request      datos de la transferencia
     * @return movimiento listo para persistir
     */
    private Transaction movement(String transferId, TransferType transferType, AccountDto account,
                                 TransactionType type, TransferRequest request) {
        Transaction transaction = transactionMapper.toDocument(ProductType.ACCOUNT, account.getId(),
                account.getCustomerId(), type, request.getAmount(), request.getDescription());
        transaction.setTransferId(transferId);
        transaction.setTransferType(transferType);
        return transaction;
    }

    /**
     * Arma la respuesta a partir de los movimientos guardados.
     *
     * @param transferId   identificador comun
     * @param transferType propia o a terceros
     * @param amount       importe
     * @param saved        movimientos persistidos
     * @return respuesta de la transferencia
     */
    private TransferResponse response(String transferId, TransferType transferType, BigDecimal amount,
                                      List<Transaction> saved) {
        TransferResponse.TransferResponseBuilder builder = TransferResponse.builder()
                .transferId(transferId)
                .transferType(transferType)
                .amount(amount);
        saved.forEach(transaction -> {
            if (transaction.getTransactionType() == TransactionType.TRANSFER_OUT) {
                builder.outgoing(transactionMapper.toResponse(transaction));
            } else {
                builder.incoming(transactionMapper.toResponse(transaction));
            }
        });
        return builder.build();
    }

    /**
     * Operacion de saldo sin comision.
     *
     * @param type   deposito o retiro
     * @param amount importe
     * @return cuerpo de la operacion
     */
    private static AccountOperationRequest operation(AccountOperationType type, BigDecimal amount) {
        return AccountOperationRequest.builder().operation(type).amount(amount).fee(BigDecimal.ZERO).build();
    }

    /**
     * Cuenta origen y destino de una transferencia.
     *
     * @param source      cuenta origen
     * @param destination cuenta destino
     */
    private record AccountPair(AccountDto source, AccountDto destination) {
    }
}
