# bank-credit-service — Diagramas de secuencia

## 1. Otorgar un credito

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CreditController
    participant S as CreditServiceImpl
    participant CC as CustomerClient
    participant CUS as customer-service
    participant R as CreditRepository
    participant DB as MongoDB (bank_credits)

    C->>CT: POST /credits (CreditRequest)
    CT->>CT: @Valid
    alt datos invalidos
        CT-->>C: 400 Bad Request
    end
    CT->>S: create(request)
    S->>CC: findById(customerId)
    CC->>CUS: GET /customers/{id}
    alt cliente inexistente
        CUS-->>CC: 404
        CT-->>C: 404 Not Found
    else customer-service caido
        CUS-->>CC: 5xx
        CT-->>C: 503 Service Unavailable
    end
    CUS-->>CC: 200 CustomerDto (type)

    alt type = PERSONAL
        alt creditType != PERSONAL
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
        S->>R: countByCustomerId(customerId)
        R->>DB: count
        alt count >= max-personal-credits
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
    else type = BUSINESS
        alt creditType != BUSINESS
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
    end

    S->>S: CreditMapper.toDocument (outstandingBalance = grantedAmount)
    S->>R: save(credit)
    R->>DB: insert
    S-->>CT: Single<CreditResponse>
    CT-->>C: 201 Created
```

## 2. Pago de credito (interno)

Invocado por `bank-transaction-service` en `POST /transactions/payment`.

```mermaid
sequenceDiagram
    autonumber
    participant TX as transaction-service
    participant CT as CreditController
    participant S as CreditServiceImpl
    participant R as CreditRepository
    participant DB as MongoDB (bank_credits)

    TX->>CT: POST /credits/{id}/balance-operations {PAYMENT, amount}
    CT->>S: applyBalanceOperation(id, request)
    S->>R: findById(id)
    alt no existe
        CT-->>TX: 404 Not Found
    end
    alt amount > outstandingBalance
        S-->>CT: BusinessRuleException
        CT-->>TX: 409 Conflict
    else
        S->>S: outstandingBalance = outstandingBalance - amount
        S->>R: save(credit)
        R->>DB: update
        S-->>CT: Single<CreditBalanceResponse>
        CT-->>TX: 200 OK
    end
```

## 3. Actualizar un credito

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CreditController
    participant S as CreditServiceImpl
    participant R as CreditRepository

    C->>CT: PUT /credits/{id} (CreditRequest)
    CT->>S: update(id, request)
    S->>R: findById(id)
    alt no existe
        CT-->>C: 404 Not Found
    end
    alt creditType o customerId distintos
        CT-->>C: 409 Conflict
    end
    S->>S: actualizar interestRate
    opt grantedAmount cambio
        alt ya recibio pagos
            CT-->>C: 409 Conflict
        else sin pagos
            S->>S: grantedAmount = outstandingBalance = nuevo importe
        end
    end
    S->>R: save(credit)
    S-->>CT: Single<CreditResponse>
    CT-->>C: 200 OK
```
