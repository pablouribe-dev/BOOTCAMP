# bank-transaction-service — Diagramas de secuencia

Todas las operaciones que mueven saldo siguen el mismo patron:

1. Obtener el producto del servicio dueno.
2. Validar las reglas propias del movimiento.
3. Pedir el cambio de saldo al servicio dueno.
4. **Solo si el paso 3 fue exitoso**, guardar la `Transaction`.

## 1. Deposito o retiro en cuenta

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as TransactionController
    participant S as TransactionServiceImpl
    participant AC as AccountClient
    participant ACC as account-service
    participant R as TransactionRepository
    participant DB as MongoDB (bank_transactions)

    C->>CT: POST /transactions/deposit | withdrawal {accountId, amount}
    CT->>CT: @Valid
    alt datos invalidos
        CT-->>C: 400 Bad Request
    end
    CT->>S: deposit(request) / withdrawal(request)

    S->>AC: findById(accountId)
    AC->>ACC: GET /accounts/{id}
    alt cuenta inexistente
        ACC-->>AC: 404
        CT-->>C: 404 Not Found
    end
    ACC-->>AC: 200 AccountDto (accountType, limite, movementDay)

    alt accountType = SAVINGS
        S->>R: countByProductIdAndCreatedAtBetween(mes en curso)
        R->>DB: count
        alt count >= monthlyMovementLimit
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
    else accountType = FIXED_TERM
        alt hoy != movementDay
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
        S->>R: countByProductIdAndCreatedAtBetween(mes en curso)
        alt ya hubo un movimiento este mes
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
    else accountType = CURRENT
        Note over S: sin restriccion
    end

    S->>AC: applyBalanceOperation(accountId, DEPOSIT | WITHDRAWAL)
    AC->>ACC: POST /accounts/{id}/balance-operations
    alt fondos insuficientes
        ACC-->>AC: 409
        AC-->>S: BusinessRuleException (mensaje remoto)
        Note over S,DB: NO se guarda la Transaction
        CT-->>C: 409 Conflict
    end
    ACC-->>AC: 200 saldo actualizado

    S->>R: save(transaction)
    R->>DB: insert
    S-->>CT: Single<TransactionResponse>
    CT-->>C: 201 Created
```

## 2. Pago de credito o de tarjeta de credito

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as TransactionController
    participant S as TransactionServiceImpl
    participant PC as CreditClient / CreditCardClient
    participant PS as credit-service / credit-card-service
    participant R as TransactionRepository
    participant DB as MongoDB (bank_transactions)

    C->>CT: POST /transactions/payment {productType, productId, amount}
    CT->>S: payment(request)
    alt productType = ACCOUNT
        S-->>CT: BusinessRuleException
        CT-->>C: 409 Conflict
    end
    S->>PC: findById(productId)
    PC->>PS: GET /credits/{id} | /credit-cards/{id}
    alt producto inexistente
        PS-->>PC: 404
        CT-->>C: 404 Not Found
    end
    PS-->>PC: 200 (customerId)
    S->>PC: applyBalanceOperation(productId, PAYMENT)
    PC->>PS: POST .../{id}/balance-operations
    alt pago mayor a lo adeudado
        PS-->>PC: 409
        Note over S,DB: NO se guarda la Transaction
        CT-->>C: 409 Conflict
    end
    PS-->>PC: 200
    S->>R: save(transaction PAYMENT)
    R->>DB: insert
    S-->>CT: Single<TransactionResponse>
    CT-->>C: 201 Created
```

## 3. Consumo con tarjeta de credito

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as TransactionController
    participant S as TransactionServiceImpl
    participant CCC as CreditCardClient
    participant CCS as credit-card-service
    participant R as TransactionRepository
    participant DB as MongoDB (bank_transactions)

    C->>CT: POST /transactions/charge {creditCardId, amount}
    CT->>S: charge(request)
    S->>CCC: findById(creditCardId)
    CCC->>CCS: GET /credit-cards/{id}
    alt tarjeta inexistente
        CCS-->>CCC: 404
        CT-->>C: 404 Not Found
    end
    CCS-->>CCC: 200 (customerId)
    S->>CCC: applyBalanceOperation(creditCardId, CHARGE)
    CCC->>CCS: POST /credit-cards/{id}/balance-operations
    alt amount > availableBalance
        CCS-->>CCC: 409
        Note over S,DB: NO se guarda la Transaction
        CT-->>C: 409 Conflict
    end
    CCS-->>CCC: 200
    S->>R: save(transaction CHARGE)
    R->>DB: insert
    S-->>CT: Single<TransactionResponse>
    CT-->>C: 201 Created
```

## 4. Movimientos de un producto

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as TransactionController
    participant S as TransactionServiceImpl
    participant R as TransactionRepository

    C->>CT: GET /transactions?productId={id}
    CT->>S: findAll(productId)
    alt productId informado
        S->>R: findByProductId(productId)
    else sin filtro
        S->>R: findAll()
    end
    R-->>S: Flux<Transaction>
    S-->>CT: Flowable<TransactionResponse>
    CT-->>C: 200 OK
```

## 5. Correccion y eliminacion administrativa

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as TransactionController
    participant S as TransactionServiceImpl
    participant R as TransactionRepository

    C->>CT: PUT /transactions/{id} {amount, description}
    CT->>S: update(id, request)
    S->>R: findById(id)
    alt no existe
        CT-->>C: 404 Not Found
    end
    S->>S: merge (sin recalcular saldos)
    S->>R: save(transaction)
    CT-->>C: 200 OK

    C->>CT: DELETE /transactions/{id}
    CT->>S: delete(id)
    S->>R: findById(id)
    S->>R: delete(transaction)
    Note over S: no recalcula saldos
    CT-->>C: 204 No Content
```

---

# Parte II

## 6. Deposito o retiro con comision por exceso de transacciones

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant S as TransactionServiceImpl
    participant AC as AccountClient
    participant RU as AccountMovementRules
    participant R as TransactionRepository
    participant ACC as account-service

    C->>S: POST /transactions/deposit {accountId, amount}
    S->>AC: findById(accountId)
    AC-->>S: AccountDto (freeTransactionLimit 10, transactionFee 1.50)
    S->>RU: validateHardRules(account)
    Note over RU: Limite duro de ahorro y dia unico del plazo fijo
    S->>RU: calculateFee(account)
    RU->>R: countByProductIdAndTransactionTypeInAndCreatedAtBetween<br/>(accountId, DEPOSIT y WITHDRAWAL, mes en curso)
    alt cantidad mayor o igual a freeTransactionLimit
        RU-->>S: fee = 1.50
    else
        RU-->>S: fee = 0
    end
    S->>AC: applyBalanceOperation(DEPOSIT, amount, fee)
    AC->>ACC: POST /accounts/{id}/balance-operations
    Note over ACC: balance += amount - fee
    ACC-->>AC: 200
    S->>R: save(transaction con feeCharged = fee)
    S-->>C: 201 Created, feeCharged 1.50
```

## 7. Transferencia con compensacion

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant T as TransferServiceImpl
    participant AC as AccountClient
    participant RU as AccountMovementRules
    participant ACC as account-service
    participant R as TransactionRepository

    C->>T: POST /transactions/transfer {sourceAccountId, destinationAccountNumber, amount}
    par
        T->>AC: findById(sourceAccountId)
    and
        T->>AC: findByAccountNumber(destinationAccountNumber)
    end
    alt destino inexistente
        T-->>C: 404
    else misma cuenta
        T-->>C: 409 must be different
    end
    T->>RU: validateHardRules(origen) y validateHardRules(destino)
    T->>AC: WITHDRAWAL en origen (fee 0)
    AC->>ACC: POST /accounts/{origen}/balance-operations
    alt fondos insuficientes
        ACC-->>T: 409
        T-->>C: 409, no se mueve ni se guarda nada
    end
    T->>AC: DEPOSIT en destino (fee 0)
    AC->>ACC: POST /accounts/{destino}/balance-operations
    alt el deposito falla (409, 503 o timeout)
        ACC--xT: error
        T->>AC: COMPENSACION: DEPOSIT en origen
        alt la compensacion tambien falla
            Note over T: log ERROR con transferId, origen,<br/>destino e importe para revision manual
        end
        T-->>C: error original del deposito, no se guarda ningun movimiento
    else deposito ok
        T->>T: transferType = OWN si es el mismo cliente, si no THIRD_PARTY
        T->>R: saveAll(TRANSFER_OUT origen, TRANSFER_IN destino, mismo transferId)
        T-->>C: 201 Created {transferId, transferType, outgoing, incoming}
    end
```

## 8. Reporte de un producto

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant RS as ReportServiceImpl
    participant PC as Account o Credit o CreditCardClient
    participant R as TransactionRepository

    C->>RS: GET /reports/products/ACCOUNT/{id}?from=2026-10-01&to=2026-10-31
    alt from posterior a to
        RS-->>C: 400
    end
    RS->>PC: findById(id) segun productType
    PC-->>RS: datos del producto y saldo actual
    RS->>R: findByProductIdAndCreatedAtBetweenOrderByCreatedAtAsc(id, desde, hasta)
    R-->>RS: movimientos
    RS->>RS: streams: groupingBy(transactionType, reducing(BigDecimal::add))<br/>y suma de comisiones
    RS-->>C: 200 product, currentBalance, movementCount, totalsByType, totalFees, movements
```

## 9. Ultimos 10 movimientos de una tarjeta

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API
    participant RS as ReportServiceImpl
    participant CCC as CreditCardClient
    participant R as TransactionRepository

    C->>RS: GET /reports/last-movements?productType=CREDIT_CARD&productId={id}
    alt productType no configurado, por ejemplo ACCOUNT
        RS-->>C: 400 only available for CREDIT_CARD
    end
    RS->>CCC: findById(id), 404 si no existe
    RS->>R: findTop10ByProductTypeAndProductIdOrderByCreatedAtDesc
    R-->>RS: hasta 10 movimientos, el mas reciente primero
    RS-->>C: 200 lista
```
