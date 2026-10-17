# Diagrama de clases - bank-debit-card-service

```mermaid
classDiagram
    direction LR
    class DebitCardController
    class DebitCardService {
        <<interface>>
        +create(DebitCardRequest) Single~DebitCardResponse~
        +findAll(customerId) Flowable~DebitCardResponse~
        +findById(String) Single~DebitCardResponse~
        +update(String, DebitCardRequest) Single~DebitCardResponse~
        +delete(String) Completable
        +addAccount(id, accountId) Single~DebitCardResponse~
        +changeMainAccount(id, accountId) Single~DebitCardResponse~
        +removeDeletedAccount(accountId) Completable
    }
    class DebitPaymentService {
        <<interface>>
        +pay(cardId, DebitPaymentRequest) Single~DebitPaymentResponse~
        +findByCard(cardId) Flowable~DebitPaymentResponse~
        +findById(cardId, paymentId) Single~DebitPaymentResponse~
        +handleOperationResult(eventType, result) Completable
    }
    class ReplicaService {
        <<interface>>
        +upsertCustomer(payload) Completable
        +deleteCustomer(customerId) Completable
        +upsertAccount(payload) Completable
        +deleteAccount(accountId) Completable
    }
    class OverdueDebtService {
        <<interface>>
        +registerOverdue(payload) Completable
        +clearOverdue(payload) Completable
        +ensureNoOverdueDebt(customerId) Completable
    }
    class DebitCardServiceImpl
    class DebitPaymentServiceImpl {
        +nextAccount(card, attempted)$ Optional~String~
    }
    class ReplicaServiceImpl
    class OverdueDebtServiceImpl
    class DebitCardEventPublisher {
        +publishUpserted(DebitCard) Completable
        +publishDeleted(DebitCard) Completable
        +requestWithdrawal(operationId, accountId, amount, cardId) Completable
    }
    class EventPublisher {
        +publish(topic, key, eventType, payload) Completable
    }
    class IdempotentEventProcessor {
        +processOnce(envelope, handler) Completable
    }
    class CustomerEventListener
    class AccountEventListener
    class DebtEventListener
    class OperationResultListener
    class DebitCard {
        String id
        String cardNumber
        String customerId
        List~String~ accountIds
        String mainAccountId
        CardStatus status
    }
    class DebitPayment {
        String id
        String cardId
        BigDecimal amount
        PaymentStatus status
        String currentOperationId
        List~String~ attemptedAccountIds
        String chargedAccountId
        String failureReason
    }
    class CustomerReplica
    class AccountReplica
    class OverdueDebt

    DebitCardController --> DebitCardService
    DebitCardController --> DebitPaymentService
    DebitCardService <|.. DebitCardServiceImpl
    DebitPaymentService <|.. DebitPaymentServiceImpl
    ReplicaService <|.. ReplicaServiceImpl
    OverdueDebtService <|.. OverdueDebtServiceImpl
    DebitCardServiceImpl --> OverdueDebtService : regla de adquisicion
    DebitCardServiceImpl --> DebitCardEventPublisher
    DebitPaymentServiceImpl --> DebitCardEventPublisher
    DebitCardEventPublisher --> EventPublisher
    CustomerEventListener --> ReplicaService
    AccountEventListener --> ReplicaService
    AccountEventListener --> DebitCardService : cuenta eliminada
    DebtEventListener --> OverdueDebtService
    OperationResultListener --> DebitPaymentService
    CustomerEventListener --> IdempotentEventProcessor
    AccountEventListener --> IdempotentEventProcessor
    DebtEventListener --> IdempotentEventProcessor
    OperationResultListener --> IdempotentEventProcessor
    DebitCardServiceImpl ..> DebitCard
    DebitCardServiceImpl ..> CustomerReplica
    DebitCardServiceImpl ..> AccountReplica
    DebitPaymentServiceImpl ..> DebitPayment
    OverdueDebtServiceImpl ..> OverdueDebt
```

Patrones: **Coreografia** (saga por eventos con fallback de cuentas),
**Observer/Pub-Sub** (listeners de Kafka), **Idempotent Consumer**
(`IdempotentEventProcessor` + `processed_events`), **Read model / copia local**
(replicas), **Adapter** (`ReactorRxAdapter`), **Builder** (Lombok).
