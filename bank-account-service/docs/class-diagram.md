# bank-account-service — Diagramas de clases

El servicio se documenta en tres vistas para que cada diagrama siga siendo legible.

## 1. Capas principales

```mermaid
classDiagram
    direction LR

    class AccountController {
        -AccountService accountService
        +create(AccountRequest) Single~AccountResponse~
        +findAll(String customerId, String accountNumber) Flowable~AccountResponse~
        +findById(String) Single~AccountResponse~
        +update(String, AccountRequest) Single~AccountResponse~
        +delete(String) Completable
        +getBalance(String) Single~BalanceResponse~
        +getAverageDailyBalance(String, YearMonth) Single~AverageDailyBalanceResponse~
        +applyBalanceOperation(String, BalanceOperationRequest) Single~BalanceResponse~
    }

    class AccountService {
        <<interface>>
    }

    class AccountServiceImpl {
        -AccountRepository accountRepository
        -AccountMapper accountMapper
        -CustomerClient customerClient
        -AccountRuleFactory accountRuleFactory
        -AccountOpeningChain accountOpeningChain
        -DailyBalanceService dailyBalanceService
        -openingContext(AccountRequest, CustomerDto) AccountOpeningContext
        -buildAccount(AccountOpeningContext) Single~Account~
        -applyOperation(Account, BalanceOperationRequest) Account
        -generateAccountNumber() Single~String~
    }

    class DailyBalanceService {
        <<interface>>
        +recordClosingBalance(String, BigDecimal) Completable
        +averageForMonth(String, LocalDate, YearMonth) Single~BigDecimal~
        +deleteHistory(String) Completable
    }

    class DailyBalanceServiceImpl {
        -DailyBalanceRepository dailyBalanceRepository
        -Clock clock
        -average(List~DailyBalance~, LocalDate, LocalDate) BigDecimal
    }

    class AccountRepository {
        <<interface>>
        +findByCustomerId(String) Flux~Account~
        +findByAccountNumber(String) Mono~Account~
        +countByCustomerIdAndAccountType(String, AccountType) Mono~Long~
        +existsByAccountNumber(String) Mono~Boolean~
    }

    class DailyBalanceRepository {
        <<interface>>
        +findByAccountIdAndDate(String, LocalDate) Mono~DailyBalance~
        +findByAccountIdAndDateLessThanEqualOrderByDateAsc(String, LocalDate) Flux~DailyBalance~
        +deleteByAccountId(String) Mono~Void~
    }

    class Account {
        <<Document accounts>>
        -String id
        -String accountNumber
        -AccountType accountType
        -String customerId
        -BigDecimal balance
        -BigDecimal maintenanceFee
        -Integer monthlyMovementLimit
        -Integer movementDay
        -List~Holder~ holders
        -List~Holder~ authorizedSigners
        -AccountProfile profile
        -BigDecimal openingAmount
        -BigDecimal minimumDailyAverage
        -Integer freeTransactionLimit
        -BigDecimal transactionFee
        -LocalDateTime createdAt
    }

    class DailyBalance {
        <<Document daily_balances>>
        -String id
        -String accountId
        -LocalDate date
        -BigDecimal closingBalance
    }

    class AccountProfile {
        <<enumeration>>
        STANDARD
        VIP
        PYME
    }

    class ReactorRxAdapter {
        <<Adapter>>
        +monoToSingle(Mono) Single
        +monoToMaybe(Mono) Maybe
        +fluxToFlowable(Flux) Flowable
        +monoToCompletable(Mono) Completable
    }

    AccountController --> AccountService
    AccountServiceImpl ..|> AccountService
    DailyBalanceServiceImpl ..|> DailyBalanceService
    AccountServiceImpl --> AccountRepository
    AccountServiceImpl --> DailyBalanceService
    DailyBalanceServiceImpl --> DailyBalanceRepository
    AccountRepository ..> Account
    DailyBalanceRepository ..> DailyBalance
    Account --> AccountProfile
    AccountServiceImpl ..> ReactorRxAdapter : usa
    DailyBalanceServiceImpl ..> ReactorRxAdapter : usa
```

## 2. Patrones de apertura: Strategy, Factory y Chain of Responsibility

```mermaid
classDiagram
    direction TB

    class AccountRule {
        <<interface>>
        +minimumOpeningAmount() BigDecimal
        +validate(AccountRequest) Completable
        +applyDefaults(Account, AccountRequest) void
    }
    class SavingsAccountRule
    class CurrentAccountRule
    class FixedTermAccountRule
    class VipSavingsAccountRule {
        -SavingsAccountRule savingsAccountRule
    }
    class PymeCurrentAccountRule {
        -CurrentAccountRule currentAccountRule
    }
    class AccountRuleFactory {
        <<Factory>>
        +getRule(AccountType, AccountProfile) AccountRule
    }
    class AccountProperties {
        <<ConfigurationProperties bank.accounts>>
        -Defaults defaults
        -ProfileSettings vip
        -ProfileSettings pyme
    }

    class AccountOpeningHandler {
        <<abstract>>
        -AccountOpeningHandler next
        +linkWith(AccountOpeningHandler) AccountOpeningHandler
        +handle(AccountOpeningContext) Completable
        #check(AccountOpeningContext) Completable
    }
    class CustomerTypeRuleHandler
    class AccountTypeRuleHandler
    class OpeningAmountHandler
    class ProfileRequirementHandler {
        <<abstract>>
        -CreditCardClient creditCardClient
        -AccountProfile profile
        -AccountType allowedType
    }
    class VipProfileHandler
    class PymeProfileHandler
    class AccountOpeningChain {
        -AccountOpeningHandler first
        +validate(AccountOpeningContext) Completable
    }
    class AccountOpeningContext {
        -AccountRequest request
        -CustomerDto customer
        -AccountProfile profile
        -AccountRule rule
    }

    SavingsAccountRule ..|> AccountRule
    CurrentAccountRule ..|> AccountRule
    FixedTermAccountRule ..|> AccountRule
    VipSavingsAccountRule ..|> AccountRule
    PymeCurrentAccountRule ..|> AccountRule
    VipSavingsAccountRule --> SavingsAccountRule : delega
    PymeCurrentAccountRule --> CurrentAccountRule : delega
    AccountRuleFactory --> AccountRule : crea/elige
    SavingsAccountRule --> AccountProperties
    CurrentAccountRule --> AccountProperties
    FixedTermAccountRule --> AccountProperties

    CustomerTypeRuleHandler --|> AccountOpeningHandler
    AccountTypeRuleHandler --|> AccountOpeningHandler
    OpeningAmountHandler --|> AccountOpeningHandler
    ProfileRequirementHandler --|> AccountOpeningHandler
    VipProfileHandler --|> ProfileRequirementHandler
    PymeProfileHandler --|> ProfileRequirementHandler
    AccountOpeningHandler --> AccountOpeningHandler : next
    AccountOpeningChain --> AccountOpeningHandler : primer eslabon
    AccountOpeningContext --> AccountRule
    AccountTypeRuleHandler ..> AccountRule : validate()
    OpeningAmountHandler ..> AccountRule : minimumOpeningAmount()
```

Orden de la cadena: `CustomerTypeRuleHandler` → `AccountTypeRuleHandler` →
`OpeningAmountHandler` → `VipProfileHandler` → `PymeProfileHandler`.

## 3. Comunicacion con otros servicios (Eureka + Resilience4j)

```mermaid
classDiagram
    direction LR

    class CustomerClient {
        -WebClient customerWebClient
        -RemoteCallDecorator remoteCallDecorator
        +findById(String) Single~CustomerDto~
    }
    class CreditCardClient {
        -WebClient creditCardWebClient
        -RemoteCallDecorator remoteCallDecorator
        +hasCreditCard(String) Single~Boolean~
    }
    class RemoteCallDecorator {
        -CircuitBreakerRegistry circuitBreakerRegistry
        -TimeLimiterRegistry timeLimiterRegistry
        +decorate(String serviceName, Mono~T~ call) Mono~T~
    }
    class WebClientConfig {
        +loadBalancedWebClientBuilder() WebClient.Builder
        +customerWebClient(WebClient.Builder) WebClient
        +creditCardWebClient(WebClient.Builder) WebClient
    }
    class ServiceUrlProperties {
        <<ConfigurationProperties bank.services>>
        -String customerServiceUrl
        -String creditCardServiceUrl
    }
    class CustomerDto {
        -String id
        -CustomerType type
        -AccountProfile profile
        -DocumentType documentType
        -String documentNumber
        -String name
    }
    class ServiceUnavailableException
    class GlobalExceptionHandler

    CustomerClient --> RemoteCallDecorator
    CreditCardClient --> RemoteCallDecorator
    CustomerClient ..> CustomerDto
    WebClientConfig --> ServiceUrlProperties
    RemoteCallDecorator ..> ServiceUnavailableException : circuito abierto, timeout o conexion rechazada
    GlobalExceptionHandler ..> ServiceUnavailableException : 503
```

## Parte III: eventos, operaciones por Kafka y deuda vencida

```mermaid
classDiagram
    direction LR
    class AccountOpeningChain
    class OverdueDebtHandler {
        #check(context) Completable
    }
    class AccountOpeningHandler {
        <<abstract>>
    }
    class OverdueDebtService {
        <<interface>>
        +ensureNoOverdueDebt(customerId) Completable
        +registerOverdue(payload) Completable
        +clearOverdue(payload) Completable
    }
    class AccountOperationService {
        <<interface>>
        +process(AccountOperationRequestedPayload) Completable
    }
    class AccountOperationServiceImpl
    class AccountServiceImpl {
        +applyBalanceOperation(id, request) Single~BalanceResponse~
    }
    class AccountEventPublisher {
        +publishUpserted(Account) Completable
        +publishDeleted(Account) Completable
        +publishOperationResult(eventType, result) Completable
    }
    class AccountOperationListener
    class DebtEventListener
    class ProcessedOperation {
        String operationId
        String resultType
        AccountOperationResultPayload result
    }
    AccountOpeningHandler <|-- OverdueDebtHandler
    AccountOpeningChain --> OverdueDebtHandler : primer eslabon
    OverdueDebtHandler --> OverdueDebtService
    AccountOperationListener --> AccountOperationService
    AccountOperationService <|.. AccountOperationServiceImpl
    AccountOperationServiceImpl --> AccountServiceImpl : misma logica, comision 0
    AccountOperationServiceImpl --> AccountEventPublisher
    AccountOperationServiceImpl ..> ProcessedOperation : idempotencia por operationId
    AccountServiceImpl --> AccountEventPublisher
    DebtEventListener --> OverdueDebtService
```
