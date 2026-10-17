# Diagrama de clases - bank-yanki-service

```mermaid
classDiagram
    direction LR
    class WalletController
    class YankiPaymentController
    class WalletService {
        <<interface>>
        +create(WalletRequest) Single~WalletResponse~
        +findAll(phoneNumber) Flowable~WalletResponse~
        +findById(String) Single~WalletResponse~
        +update(String, WalletRequest) Single~WalletResponse~
        +delete(String) Completable
        +linkDebitCard(id, cardNumber) Single~WalletResponse~
        +unlinkDebitCard(id) Single~WalletResponse~
        +upsertDebitCard(payload) Completable
        +deleteDebitCard(cardId) Completable
    }
    class YankiPaymentService {
        <<interface>>
        +send(YankiPaymentRequest) Single~YankiPaymentResponse~
        +findById(String) Single~YankiPaymentResponse~
        +findAll(phoneNumber) Flowable~YankiPaymentResponse~
        +handleOperationResult(eventType, result) Completable
        +expireStalePayments() Single~Long~
    }
    class WalletServiceImpl
    class YankiPaymentServiceImpl {
        -startCardDebit()
        -debitWallet()
        -creditReceiver()
        -compensate(payment, reason)
        -complete(payment)
        -finishFailed(payment, reason)
    }
    class YankiEventPublisher {
        +requestOperation(operationId, accountId, operation, amount, paymentId) Completable
        +publishOutcome(YankiPayment) Completable
    }
    class EventPublisher
    class IdempotentEventProcessor
    class DebitCardEventListener
    class OperationResultListener
    class StalePaymentScheduler {
        +expireStalePayments()
    }
    class Wallet {
        String id
        DocumentType documentType
        String documentNumber
        String phoneNumber
        String imei
        String email
        BigDecimal balance
        String debitCardNumber
        Long version
    }
    class YankiPayment {
        String id
        String fromPhoneNumber
        String toPhoneNumber
        BigDecimal amount
        PaymentStatus status
        PaymentStep step
        String senderAccountId
        String receiverAccountId
        String debitOperationId
        String creditOperationId
        String compensationOperationId
        boolean debitCompleted
        String failureReason
    }
    class DebitCardReplica {
        String cardId
        String cardNumber
        String mainAccountId
        String status
    }
    class PaymentStep {
        <<enumeration>>
        DEBIT_REQUESTED
        CREDIT_REQUESTED
        COMPENSATING
        DONE
    }

    WalletController --> WalletService
    YankiPaymentController --> YankiPaymentService
    WalletService <|.. WalletServiceImpl
    YankiPaymentService <|.. YankiPaymentServiceImpl
    YankiPaymentServiceImpl --> YankiEventPublisher
    YankiEventPublisher --> EventPublisher
    DebitCardEventListener --> WalletService
    OperationResultListener --> YankiPaymentService
    DebitCardEventListener --> IdempotentEventProcessor
    OperationResultListener --> IdempotentEventProcessor
    StalePaymentScheduler --> YankiPaymentService
    WalletServiceImpl ..> Wallet
    WalletServiceImpl ..> DebitCardReplica
    YankiPaymentServiceImpl ..> YankiPayment
    YankiPaymentServiceImpl ..> Wallet
    YankiPaymentServiceImpl ..> DebitCardReplica
    YankiPayment --> PaymentStep
```

Patrones: **Saga con compensacion** (orquestada por `YankiPaymentServiceImpl`,
pasos en `PaymentStep`), **Idempotent Consumer**, **Read model** (copia de
tarjetas), **Optimistic locking** (`@Version` en `Wallet`), **Adapter**,
**Builder**.
