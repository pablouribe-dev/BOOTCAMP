# bank-credit-card-service — Diagrama de clases

```mermaid
classDiagram
    direction LR

    class CreditCardController {
        -CreditCardService creditCardService
        +create(CreditCardRequest) Single~CreditCardResponse~
        +findAll(String customerId) Flowable~CreditCardResponse~
        +findById(String) Single~CreditCardResponse~
        +update(String, CreditCardRequest) Single~CreditCardResponse~
        +delete(String) Completable
        +getBalance(String) Single~CardBalanceResponse~
        +applyBalanceOperation(String, CardOperationRequest) Single~CardBalanceResponse~
    }

    class CreditCardService {
        <<interface>>
        +create(CreditCardRequest) Single~CreditCardResponse~
        +findAll(String) Flowable~CreditCardResponse~
        +findById(String) Single~CreditCardResponse~
        +update(String, CreditCardRequest) Single~CreditCardResponse~
        +delete(String) Completable
        +getBalance(String) Single~CardBalanceResponse~
        +applyBalanceOperation(String, CardOperationRequest) Single~CardBalanceResponse~
    }

    class CreditCardServiceImpl {
        -CreditCardRepository creditCardRepository
        -CreditCardMapper creditCardMapper
        -CustomerClient customerClient
        -CardRulesProperties cardRules
        -validateIssue(CustomerDto, CreditCardRequest) Completable
        -buildCard(CreditCardRequest) Single~CreditCard~
        -generateCardNumber() Single~String~
        -merge(CreditCard, CreditCardRequest) CreditCard
        -applyOperation(CreditCard, CardOperationRequest) CreditCard
    }

    class CreditCardRepository {
        <<interface>>
        +findByCustomerId(String) Flux~CreditCard~
        +findByCardNumber(String) Mono~CreditCard~
        +existsByCardNumber(String) Mono~Boolean~
    }

    class ReactiveMongoRepository {
        <<interface>>
    }

    class CreditCardMapper {
        +toDocument(CreditCardRequest) CreditCard
        +toResponse(CreditCard) CreditCardResponse
        +toBalanceResponse(CreditCard) CardBalanceResponse
        +availableBalance(CreditCard) BigDecimal
    }

    class CreditCard {
        <<Document credit_cards>>
        -String id
        -String cardNumber
        -CardType cardType
        -String customerId
        -BigDecimal creditLimit
        -BigDecimal usedAmount
        -LocalDateTime createdAt
    }

    class CardType {
        <<enumeration>>
        PERSONAL
        BUSINESS
    }

    class CardOperationType {
        <<enumeration>>
        CHARGE
        PAYMENT
    }

    class CreditCardRequest {
        -CardType cardType
        -String customerId
        -BigDecimal creditLimit
    }

    class CreditCardResponse {
        -BigDecimal availableBalance
    }

    class CardBalanceResponse {
        -BigDecimal availableBalance
    }

    class CardOperationRequest {
        -CardOperationType operation
        -BigDecimal amount
    }

    class CustomerClient {
        -WebClient customerWebClient
        +findById(String) Single~CustomerDto~
    }

    class CustomerDto {
        -String id
        -CustomerType type
        -String name
    }

    class CustomerType {
        <<enumeration>>
        PERSONAL
        BUSINESS
    }

    class ServiceUrlProperties {
        <<ConfigurationProperties bank.services>>
        -String customerServiceUrl
    }

    class CardRulesProperties {
        <<ConfigurationProperties bank.rules>>
        -String cardNumberPrefix
    }

    class WebClientConfig {
        +customerWebClient(WebClient.Builder) WebClient
    }

    class GlobalExceptionHandler
    class ErrorResponse
    class ResourceNotFoundException
    class BusinessRuleException
    class ServiceUnavailableException

    CreditCardController --> CreditCardService
    CreditCardServiceImpl ..|> CreditCardService
    CreditCardServiceImpl --> CreditCardRepository
    CreditCardServiceImpl --> CreditCardMapper
    CreditCardServiceImpl --> CustomerClient
    CreditCardServiceImpl --> CardRulesProperties
    CreditCardRepository --|> ReactiveMongoRepository
    CreditCard --> CardType
    CardOperationRequest --> CardOperationType
    CreditCardMapper ..> CreditCard
    CreditCardMapper ..> CreditCardResponse
    CreditCardMapper ..> CardBalanceResponse
    CustomerClient ..> CustomerDto
    CustomerDto --> CustomerType
    WebClientConfig --> ServiceUrlProperties
    GlobalExceptionHandler ..> ErrorResponse
    CreditCardServiceImpl ..> BusinessRuleException : lanza
    CreditCardServiceImpl ..> ResourceNotFoundException : lanza
    CustomerClient ..> ServiceUnavailableException : lanza
```

## Parte II: Eureka, Resilience4j y adaptador Reactor - RxJava

```mermaid
classDiagram
    direction LR

    class CustomerClient {
        -WebClient customerWebClient
        -RemoteCallDecorator remoteCallDecorator
        +findById(String) Single~CustomerDto~
    }
    class RemoteCallDecorator {
        -CircuitBreakerRegistry circuitBreakerRegistry
        -TimeLimiterRegistry timeLimiterRegistry
        +decorate(String serviceName, Mono~T~ call) Mono~T~
    }
    class WebClientConfig {
        +loadBalancedWebClientBuilder() WebClient.Builder
        +customerWebClient(WebClient.Builder) WebClient
    }
    class ReactorRxAdapter {
        <<Adapter>>
        +monoToSingle(Mono) Single
        +monoToMaybe(Mono) Maybe
        +fluxToFlowable(Flux) Flowable
        +monoToCompletable(Mono) Completable
    }
    class ServiceUnavailableException
    class GlobalExceptionHandler

    CustomerClient --> RemoteCallDecorator : circuit breaker y timeout 2 s
    CustomerClient ..> ReactorRxAdapter
    WebClientConfig ..> CustomerClient : WebClient balanceado (http://bank-customer-service)
    RemoteCallDecorator ..> ServiceUnavailableException : circuito abierto, timeout o conexion rechazada
    GlobalExceptionHandler ..> ServiceUnavailableException : 503
```

La excepcion `RemoteServiceException` de la Parte I ahora se llama `ServiceUnavailableException`.
Las excepciones de negocio (`ResourceNotFoundException`, `BusinessRuleException`)
no cuentan como fallo del circuito.

## Parte III: deuda vencida

```mermaid
classDiagram
    direction LR
    class CreditCardServiceImpl {
        -saveAndNotifySettlement(CreditCard) Single~CreditCard~
    }
    class OverdueScanScheduler {
        +scanOverdueDebts()
    }
    class OverdueScanService {
        <<interface>>
        +scan() Single~OverdueScanResponse~
    }
    class OverdueScanServiceImpl {
        +isOverdue(product, today)$ boolean
    }
    class CardDebtEventPublisher {
        +publishDetected(CreditCard) Completable
        +publishCleared(CreditCard) Completable
    }
    class OverdueDebtService {
        <<interface>>
        +ensureNoOverdueDebt(customerId) Completable
    }
    class DebtEventListener
    class AdminController
    OverdueScanScheduler --> OverdueScanService : cron diario
    AdminController --> OverdueScanService : POST /admin/overdue-debts/scan
    OverdueScanService <|.. OverdueScanServiceImpl
    OverdueScanServiceImpl --> CardDebtEventPublisher : DEBT_OVERDUE_DETECTED
    CreditCardServiceImpl --> CardDebtEventPublisher : DEBT_OVERDUE_CLEARED
    CreditCardServiceImpl --> OverdueDebtService : regla de adquisicion
    DebtEventListener --> OverdueDebtService : copia local overdue_debts
```
