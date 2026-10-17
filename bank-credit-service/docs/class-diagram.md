# bank-credit-service — Diagrama de clases

```mermaid
classDiagram
    direction LR

    class CreditController {
        -CreditService creditService
        +create(CreditRequest) Single~CreditResponse~
        +findAll(String customerId) Flowable~CreditResponse~
        +findById(String) Single~CreditResponse~
        +update(String, CreditRequest) Single~CreditResponse~
        +delete(String) Completable
        +getBalance(String) Single~CreditBalanceResponse~
        +applyBalanceOperation(String, CreditOperationRequest) Single~CreditBalanceResponse~
    }

    class CreditService {
        <<interface>>
        +create(CreditRequest) Single~CreditResponse~
        +findAll(String) Flowable~CreditResponse~
        +findById(String) Single~CreditResponse~
        +update(String, CreditRequest) Single~CreditResponse~
        +delete(String) Completable
        +getBalance(String) Single~CreditBalanceResponse~
        +applyBalanceOperation(String, CreditOperationRequest) Single~CreditBalanceResponse~
    }

    class CreditServiceImpl {
        -CreditRepository creditRepository
        -CreditMapper creditMapper
        -CustomerClient customerClient
        -CreditRulesProperties creditRules
        -validateGrant(CustomerDto, CreditRequest) Completable
        -validatePersonalGrant(CreditRequest) Completable
        -validateBusinessGrant(CreditRequest) Completable
        -merge(Credit, CreditRequest) Credit
        -applyPayment(Credit, BigDecimal) Credit
    }

    class CreditRepository {
        <<interface>>
        +findByCustomerId(String) Flux~Credit~
        +countByCustomerId(String) Mono~Long~
    }

    class ReactiveMongoRepository {
        <<interface>>
    }

    class CreditMapper {
        +toDocument(CreditRequest) Credit
        +toResponse(Credit) CreditResponse
        +toBalanceResponse(Credit) CreditBalanceResponse
    }

    class Credit {
        <<Document credits>>
        -String id
        -CreditType creditType
        -String customerId
        -BigDecimal grantedAmount
        -BigDecimal outstandingBalance
        -BigDecimal interestRate
        -LocalDateTime createdAt
    }

    class CreditType {
        <<enumeration>>
        PERSONAL
        BUSINESS
    }

    class CreditOperationType {
        <<enumeration>>
        PAYMENT
    }

    class CreditRequest {
        -CreditType creditType
        -String customerId
        -BigDecimal grantedAmount
        -BigDecimal interestRate
    }

    class CreditResponse
    class CreditBalanceResponse

    class CreditOperationRequest {
        -CreditOperationType operation
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

    class CreditRulesProperties {
        <<ConfigurationProperties bank.rules>>
        -Integer maxPersonalCredits
    }

    class WebClientConfig {
        +customerWebClient(WebClient.Builder) WebClient
    }

    class GlobalExceptionHandler
    class ErrorResponse
    class ResourceNotFoundException
    class BusinessRuleException
    class ServiceUnavailableException

    CreditController --> CreditService
    CreditServiceImpl ..|> CreditService
    CreditServiceImpl --> CreditRepository
    CreditServiceImpl --> CreditMapper
    CreditServiceImpl --> CustomerClient
    CreditServiceImpl --> CreditRulesProperties
    CreditRepository --|> ReactiveMongoRepository
    Credit --> CreditType
    CreditOperationRequest --> CreditOperationType
    CreditMapper ..> Credit
    CreditMapper ..> CreditResponse
    CreditMapper ..> CreditBalanceResponse
    CustomerClient ..> CustomerDto
    CustomerDto --> CustomerType
    WebClientConfig --> ServiceUrlProperties
    GlobalExceptionHandler ..> ErrorResponse
    CreditServiceImpl ..> BusinessRuleException : lanza
    CreditServiceImpl ..> ResourceNotFoundException : lanza
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
    class CreditServiceImpl {
        -saveAndNotifySettlement(Credit) Single~Credit~
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
    class CreditDebtEventPublisher {
        +publishDetected(Credit) Completable
        +publishCleared(Credit) Completable
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
    OverdueScanServiceImpl --> CreditDebtEventPublisher : DEBT_OVERDUE_DETECTED
    CreditServiceImpl --> CreditDebtEventPublisher : DEBT_OVERDUE_CLEARED
    CreditServiceImpl --> OverdueDebtService : regla de adquisicion
    DebtEventListener --> OverdueDebtService : copia local overdue_debts
```
