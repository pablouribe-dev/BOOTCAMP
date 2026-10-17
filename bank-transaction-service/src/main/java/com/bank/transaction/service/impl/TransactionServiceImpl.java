package com.bank.transaction.service.impl;

import com.bank.transaction.adapter.ReactorRxAdapter;
import com.bank.transaction.client.AccountClient;
import com.bank.transaction.client.AccountOperationRequest;
import com.bank.transaction.client.AccountOperationType;
import com.bank.transaction.client.CardOperationRequest;
import com.bank.transaction.client.CardOperationType;
import com.bank.transaction.client.CreditCardClient;
import com.bank.transaction.client.CreditClient;
import com.bank.transaction.client.CreditOperationRequest;
import com.bank.transaction.client.CreditOperationType;
import com.bank.transaction.client.CustomerClient;
import com.bank.transaction.dto.AccountTransactionRequest;
import com.bank.transaction.dto.ChargeRequest;
import com.bank.transaction.dto.PaymentRequest;
import com.bank.transaction.dto.TransactionResponse;
import com.bank.transaction.dto.TransactionUpdateRequest;
import com.bank.transaction.exception.BusinessRuleException;
import com.bank.transaction.exception.ResourceNotFoundException;
import com.bank.transaction.mapper.TransactionMapper;
import com.bank.transaction.model.ProductType;
import com.bank.transaction.model.Transaction;
import com.bank.transaction.model.TransactionType;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.TransactionService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Implementacion de las reglas de negocio de los movimientos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AccountClient accountClient;
    private final CreditClient creditClient;
    private final CreditCardClient creditCardClient;
    private final CustomerClient customerClient;
    private final AccountMovementRules accountMovementRules;

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TransactionResponse> deposit(AccountTransactionRequest request) {
        log.info("Registering a deposit of {} on account id={}", request.getAmount(), request.getAccountId());
        return handleAccountMovement(request, TransactionType.DEPOSIT, AccountOperationType.DEPOSIT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TransactionResponse> withdrawal(AccountTransactionRequest request) {
        log.info("Registering a withdrawal of {} on account id={}", request.getAmount(), request.getAccountId());
        return handleAccountMovement(request, TransactionType.WITHDRAWAL, AccountOperationType.WITHDRAWAL);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Los pagos solo aplican a creditos y a tarjetas de credito. Parte III:
     * cualquier cliente existente puede pagar el producto de otro
     * ({@code payerCustomerId}).</p>
     */
    @Override
    public Single<TransactionResponse> payment(PaymentRequest request) {
        log.info("Registering a payment of {} on {} id={}",
                request.getAmount(), request.getProductType(), request.getProductId());
        return switch (request.getProductType()) {
            case CREDIT -> payCredit(request);
            case CREDIT_CARD -> payCreditCard(request);
            case ACCOUNT, DEBIT_CARD -> Single.error(new BusinessRuleException(
                    "Payments only apply to CREDIT and CREDIT_CARD products"));
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TransactionResponse> charge(ChargeRequest request) {
        log.info("Registering a charge of {} on credit card id={}", request.getAmount(), request.getCreditCardId());
        return creditCardClient.findById(request.getCreditCardId())
                .flatMap(card -> creditCardClient.applyBalanceOperation(request.getCreditCardId(),
                                CardOperationRequest.builder()
                                        .operation(CardOperationType.CHARGE)
                                        .amount(request.getAmount())
                                        .build())
                        .andThen(Single.just(card)))
                .flatMap(card -> save(transactionMapper.toDocument(ProductType.CREDIT_CARD,
                        request.getCreditCardId(), card.getCustomerId(), TransactionType.CHARGE,
                        request.getAmount(), request.getDescription())))
                .map(transactionMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flowable<TransactionResponse> findAll(String productId) {
        boolean filtered = productId != null && !productId.isBlank();
        log.debug("Listing transactions{}", filtered ? " for productId=" + productId : "");
        Flux<Transaction> source = filtered
                ? transactionRepository.findByProductId(productId)
                : transactionRepository.findAll();
        return ReactorRxAdapter.fluxToFlowable(source).map(transactionMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TransactionResponse> findById(String id) {
        log.debug("Looking for transaction with id={}", id);
        return findOrFail(id).map(transactionMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Single<TransactionResponse> update(String id, TransactionUpdateRequest request) {
        log.info("Updating transaction with id={} (balances are not recalculated)", id);
        return findOrFail(id)
                .map(existing -> transactionMapper.merge(existing, request))
                .flatMap(updated -> ReactorRxAdapter.monoToSingle(transactionRepository.save(updated)))
                .map(transactionMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Completable delete(String id) {
        log.info("Deleting transaction with id={} (balances are not recalculated)", id);
        return findOrFail(id)
                .flatMapCompletable(transaction ->
                        ReactorRxAdapter.monoToCompletable(transactionRepository.delete(transaction)))
                .doOnComplete(() -> log.info("Transaction with id={} deleted", id));
    }

    /**
     * Recupera un movimiento o falla con 404 si no existe.
     *
     * @param id identificador del movimiento
     * @return el documento encontrado
     */
    private Single<Transaction> findOrFail(String id) {
        return ReactorRxAdapter.monoToMaybe(transactionRepository.findById(id))
                .switchIfEmpty(Single.error(() ->
                        new ResourceNotFoundException("Transaction not found with id " + id)));
    }

    /**
     * Orquesta un deposito o un retiro de cuenta.
     *
     * <ol>
     *   <li>Obtiene la cuenta.</li>
     *   <li>Valida las reglas duras de su tipo.</li>
     *   <li>Calcula la comision por exceso de transacciones.</li>
     *   <li>Pide el cambio de saldo con esa comision.</li>
     *   <li>Solo si el paso anterior fue exitoso, registra el movimiento.</li>
     * </ol>
     *
     * @param request         cuenta, importe y descripcion
     * @param transactionType tipo de movimiento a registrar
     * @param operation       operacion a pedir al servicio de cuentas
     * @return el movimiento registrado
     */
    private Single<TransactionResponse> handleAccountMovement(AccountTransactionRequest request,
                                                              TransactionType transactionType,
                                                              AccountOperationType operation) {
        return accountClient.findById(request.getAccountId())
                .flatMap(account -> accountMovementRules.validateHardRules(account)
                        .andThen(accountMovementRules.calculateFee(account))
                        .flatMap(fee -> accountClient.applyBalanceOperation(request.getAccountId(),
                                        AccountOperationRequest.builder()
                                                .operation(operation)
                                                .amount(request.getAmount())
                                                .fee(fee)
                                                .build())
                                .andThen(Single.defer(() -> {
                                    Transaction transaction = transactionMapper.toDocument(ProductType.ACCOUNT,
                                            request.getAccountId(), account.getCustomerId(), transactionType,
                                            request.getAmount(), request.getDescription());
                                    transaction.setFeeCharged(fee);
                                    return save(transaction);
                                }))))
                .map(transactionMapper::toResponse);
    }

    /**
     * Orquesta el pago de un credito.
     *
     * @param request producto, importe y descripcion
     * @return el movimiento registrado
     */
    private Single<TransactionResponse> payCredit(PaymentRequest request) {
        return creditClient.findById(request.getProductId())
                .flatMap(credit -> resolvePayer(request, credit.getCustomerId())
                        .flatMap(payer -> creditClient.applyBalanceOperation(request.getProductId(),
                                        CreditOperationRequest.builder()
                                                .operation(CreditOperationType.PAYMENT)
                                                .amount(request.getAmount())
                                                .build())
                                .andThen(Single.defer(() -> savePayment(ProductType.CREDIT, request,
                                        credit.getCustomerId(), payer)))))
                .map(transactionMapper::toResponse);
    }

    /**
     * Orquesta el pago de una tarjeta de credito.
     *
     * @param request producto, importe y descripcion
     * @return el movimiento registrado
     */
    private Single<TransactionResponse> payCreditCard(PaymentRequest request) {
        return creditCardClient.findById(request.getProductId())
                .flatMap(card -> resolvePayer(request, card.getCustomerId())
                        .flatMap(payer -> creditCardClient.applyBalanceOperation(request.getProductId(),
                                        CardOperationRequest.builder()
                                                .operation(CardOperationType.PAYMENT)
                                                .amount(request.getAmount())
                                                .build())
                                .andThen(Single.defer(() -> savePayment(ProductType.CREDIT_CARD, request,
                                        card.getCustomerId(), payer)))))
                .map(transactionMapper::toResponse);
    }

    /**
     * Parte III: determina quien paga. Sin {@code payerCustomerId}, o si es el
     * propio titular, paga el titular; si es otro cliente, se valida que exista
     * (REST a {@code bank-customer-service}, con circuit breaker).
     *
     * @param request  pago solicitado
     * @param holderId titular del producto
     * @return identificador del pagador
     */
    private Single<String> resolvePayer(PaymentRequest request, String holderId) {
        String payer = request.getPayerCustomerId();
        if (payer == null || payer.isBlank() || payer.equals(holderId)) {
            return Single.just(holderId);
        }
        log.info("Third-party payment: customer {} pays a product of customer {}", payer, holderId);
        return customerClient.findById(payer).map(customer -> payer);
    }

    /**
     * Registra el pago con el pagador y la marca de tercero.
     *
     * @param productType tipo de producto pagado
     * @param request     pago solicitado
     * @param holderId    titular del producto
     * @param payerId     cliente que paga
     * @return el movimiento persistido
     */
    private Single<Transaction> savePayment(ProductType productType, PaymentRequest request, String holderId,
                                            String payerId) {
        Transaction transaction = transactionMapper.toDocument(productType, request.getProductId(), holderId,
                TransactionType.PAYMENT, request.getAmount(), request.getDescription());
        transaction.setPayerCustomerId(payerId);
        transaction.setThirdParty(!payerId.equals(holderId));
        return save(transaction);
    }

    /**
     * Persiste el movimiento, una vez que el cambio de saldo fue exitoso.
     *
     * @param transaction movimiento a guardar
     * @return el movimiento persistido
     */
    private Single<Transaction> save(Transaction transaction) {
        return ReactorRxAdapter.monoToSingle(transactionRepository.save(transaction))
                .doOnSuccess(saved -> log.info("{} of {} (fee {}) registered on {} id={} as transaction id={}",
                        saved.getTransactionType(), saved.getAmount(), saved.getFeeCharged(),
                        saved.getProductType(), saved.getProductId(), saved.getId()));
    }
}
