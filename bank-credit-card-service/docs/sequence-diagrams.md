# bank-credit-card-service — Diagramas de secuencia

## 1. Emitir una tarjeta

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CreditCardController
    participant S as CreditCardServiceImpl
    participant CC as CustomerClient
    participant CUS as customer-service
    participant R as CreditCardRepository
    participant DB as MongoDB (bank_credit_cards)

    C->>CT: POST /credit-cards (CreditCardRequest)
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
    alt cardType != tipo de cliente
        S-->>CT: BusinessRuleException
        CT-->>C: 409 Conflict
    end
    loop hasta encontrar un numero libre
        S->>R: existsByCardNumber(prefijo + digitos)
    end
    S->>S: usedAmount = 0, createdAt = ahora
    S->>R: save(card)
    R->>DB: insert
    S-->>CT: Single<CreditCardResponse> (availableBalance calculado)
    CT-->>C: 201 Created
```

## 2. Consumo o pago (interno)

Invocado por `bank-transaction-service` en `POST /transactions/charge` y
`POST /transactions/payment`.

```mermaid
sequenceDiagram
    autonumber
    participant TX as transaction-service
    participant CT as CreditCardController
    participant S as CreditCardServiceImpl
    participant R as CreditCardRepository
    participant DB as MongoDB (bank_credit_cards)

    TX->>CT: POST /credit-cards/{id}/balance-operations {operation, amount}
    CT->>S: applyBalanceOperation(id, request)
    S->>R: findById(id)
    alt no existe
        CT-->>TX: 404 Not Found
    end
    alt operation = CHARGE
        alt amount > creditLimit - usedAmount
            S-->>CT: BusinessRuleException
            CT-->>TX: 409 Conflict (limite excedido)
        else
            S->>S: usedAmount = usedAmount + amount
        end
    else operation = PAYMENT
        alt amount > usedAmount
            S-->>CT: BusinessRuleException
            CT-->>TX: 409 Conflict
        else
            S->>S: usedAmount = usedAmount - amount
        end
    end
    S->>R: save(card)
    R->>DB: update
    S-->>CT: Single<CardBalanceResponse>
    CT-->>TX: 200 OK
```

## 3. Actualizar una tarjeta

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CreditCardController
    participant S as CreditCardServiceImpl
    participant R as CreditCardRepository

    C->>CT: PUT /credit-cards/{id} (CreditCardRequest)
    CT->>S: update(id, request)
    S->>R: findById(id)
    alt no existe
        CT-->>C: 404 Not Found
    end
    alt cardType o customerId distintos
        CT-->>C: 409 Conflict
    else creditLimit < usedAmount
        CT-->>C: 409 Conflict
    end
    S->>S: actualizar creditLimit
    S->>R: save(card)
    S-->>CT: Single<CreditCardResponse>
    CT-->>C: 200 OK
```
