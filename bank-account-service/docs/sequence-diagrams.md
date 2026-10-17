# bank-account-service — Diagramas de secuencia

## 1. Abrir una cuenta

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as AccountController
    participant S as AccountServiceImpl
    participant CC as CustomerClient
    participant CUS as customer-service
    participant R as AccountRepository
    participant DB as MongoDB (bank_accounts)

    C->>CT: POST /accounts (AccountRequest)
    CT->>CT: @Valid
    alt datos invalidos
        CT-->>C: 400 Bad Request
    end
    CT->>S: create(request)
    S->>CC: findById(customerId)
    CC->>CUS: GET /customers/{id}
    alt cliente inexistente (4xx)
        CUS-->>CC: 404
        CC-->>S: ResourceNotFoundException
        S-->>CT: error
        CT-->>C: 404 Not Found
    else customer-service caido (5xx)
        CUS-->>CC: 5xx
        CC-->>S: ServiceUnavailableException
        CT-->>C: 503 Service Unavailable
    end
    CUS-->>CC: 200 CustomerDto (type)

    alt type = PERSONAL
        alt accountType = FIXED_TERM
            S->>S: validar movementDay presente
        else SAVINGS o CURRENT
            S->>R: countByCustomerIdAndAccountType
            R->>DB: count
            alt count > 0
                S-->>CT: BusinessRuleException
                CT-->>C: 409 Conflict
            end
        end
    else type = BUSINESS
        alt accountType != CURRENT
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        else holders vacio
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
    end

    loop hasta encontrar un numero libre
        S->>R: existsByAccountNumber(candidato)
    end
    S->>S: aplicar comision y limites del tipo (Config Server)
    S->>S: titular = cliente (PERSONAL) o holders (BUSINESS)
    S->>R: save(account)
    R->>DB: insert
    R-->>S: Mono<Account>
    S-->>CT: Single<AccountResponse>
    CT-->>C: 201 Created
```

## 2. Operacion de saldo (interna)

Invocada por `bank-transaction-service` en depositos y retiros.

```mermaid
sequenceDiagram
    autonumber
    participant TX as transaction-service
    participant CT as AccountController
    participant S as AccountServiceImpl
    participant R as AccountRepository
    participant DB as MongoDB (bank_accounts)

    TX->>CT: POST /accounts/{id}/balance-operations {operation, amount}
    CT->>S: applyBalanceOperation(id, request)
    S->>R: findById(id)
    alt no existe
        S-->>CT: ResourceNotFoundException
        CT-->>TX: 404 Not Found
    end
    alt operation = DEPOSIT
        S->>S: balance = balance + amount
    else operation = WITHDRAWAL
        alt balance < amount
            S-->>CT: BusinessRuleException
            CT-->>TX: 409 Conflict (fondos insuficientes)
        else
            S->>S: balance = balance - amount
        end
    end
    S->>R: save(account)
    R->>DB: update
    S-->>CT: Single<BalanceResponse>
    CT-->>TX: 200 OK
```

## 3. Consultar saldo

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as AccountController
    participant S as AccountServiceImpl
    participant R as AccountRepository

    C->>CT: GET /accounts/{id}/balance
    CT->>S: getBalance(id)
    S->>R: findById(id)
    alt no existe
        S-->>CT: ResourceNotFoundException
        CT-->>C: 404 Not Found
    else existe
        S-->>CT: Single<BalanceResponse>
        CT-->>C: 200 OK
    end
```

## 4. Actualizar una cuenta

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as AccountController
    participant S as AccountServiceImpl
    participant R as AccountRepository

    C->>CT: PUT /accounts/{id} (AccountRequest)
    CT->>S: update(id, request)
    S->>R: findById(id)
    alt no existe
        CT-->>C: 404 Not Found
    end
    alt accountType o customerId distintos
        S-->>CT: BusinessRuleException
        CT-->>C: 409 Conflict
    end
    S->>S: AccountMapper.merge (sin tocar el saldo)
    S->>R: save(account)
    S-->>CT: Single<AccountResponse>
    CT-->>C: 200 OK
```

---

# Parte II

## 5. Apertura de cuenta VIP o PYME (Strategy + Factory + Chain of Responsibility)

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant GW as API Gateway :8080
    participant CT as AccountController
    participant S as AccountServiceImpl
    participant CC as CustomerClient
    participant F as AccountRuleFactory
    participant CH as AccountOpeningChain
    participant CCC as CreditCardClient
    participant CARD as credit-card-service
    participant R as AccountRepository
    participant DB as DailyBalanceService

    C->>GW: POST /accounts {SAVINGS, customerId, openingAmount}
    GW->>CT: lb://bank-account-service
    CT->>S: create(request)
    S->>CC: findById(customerId)
    CC-->>S: CustomerDto (type PERSONAL, profile VIP)
    S->>F: getRule(SAVINGS, VIP)
    F-->>S: VipSavingsAccountRule
    S->>CH: validate(context)
    Note over CH: 1. Reglas por tipo de cliente (Parte I)<br/>2. Reglas del tipo de cuenta (estrategia)<br/>3. Monto de apertura >= minimo VIP
    alt openingAmount menor al minimo
        CH-->>CT: BusinessRuleException
        CT-->>C: 409 opening amount must be at least 500
    end
    Note over CH: 4. Perfil VIP: solo SAVINGS
    CH->>CCC: hasCreditCard(customerId)
    CCC->>CARD: GET /credit-cards?customerId=
    CARD-->>CCC: lista de tarjetas
    alt sin tarjetas
        CH-->>CT: BusinessRuleException
        CT-->>C: 409 requires the customer to hold a credit card
    end
    Note over CH: 5. Perfil PYME (no aplica a VIP)
    CH-->>S: ok
    S->>S: buildAccount: numero, saldo = openingAmount, perfil,<br/>rule.applyDefaults (minimumDailyAverage, freeTransactionLimit, transactionFee)
    S->>R: save(account)
    S->>DB: recordClosingBalance(accountId, openingAmount)
    S-->>CT: Single AccountResponse
    CT-->>C: 201 Created (profile VIP)
```

Para PYME el recorrido es el mismo: la fabrica devuelve `PymeCurrentAccountRule`
(comision de mantenimiento 0) y el eslabon 5 exige `CURRENT` y una tarjeta.

## 6. Llamada con circuito abierto (Resilience4j)

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant S as AccountServiceImpl
    participant CC as CustomerClient
    participant D as RemoteCallDecorator
    participant CB as CircuitBreaker bank-customer-service
    participant TL as TimeLimiter 2 s
    participant CUS as customer-service caido

    C->>S: POST /accounts
    S->>CC: findById(customerId)
    CC->>D: decorate(bank-customer-service, call)
    D->>CB: permiso?
    alt circuito CERRADO
        CB->>TL: ejecutar con limite de 2 s
        TL->>CUS: GET /customers/{id}
        CUS--xTL: sin respuesta o conexion rechazada
        TL-->>CB: TimeoutException (falla registrada)
        CB-->>D: error
        D-->>C: 503 did not answer within the time limit
        Note over CB: Con 50% de fallos en la ventana,<br/>el circuito pasa a ABIERTO
    else circuito ABIERTO
        CB-->>D: CallNotPermittedException (sin llamar al servicio)
        D-->>C: 503 is not available (circuit breaker open), en ~10 ms
    end
    Note over CB: A los 10 s pasa a SEMIABIERTO y prueba 3 llamadas
```

## 7. Promedio diario del mes (VIP)

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant S as AccountServiceImpl
    participant D as DailyBalanceServiceImpl
    participant R as DailyBalanceRepository

    C->>S: GET /accounts/{id}/average-daily-balance?month=2026-10
    S->>S: findById(id)
    S->>D: averageForMonth(id, fechaApertura, 2026-10)
    D->>R: findByAccountIdAndDateLessThanEqualOrderByDateAsc(id, min(finDeMes, hoy))
    R-->>D: saldos de cierre por dia
    D->>D: para cada dia, ultimo saldo conocido (arrastre)<br/>promedio con streams
    D-->>S: promedio
    S-->>C: 200 averageDailyBalance, minimumDailyAverage, meetsMinimum
```
