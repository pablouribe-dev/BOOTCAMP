# Diagramas de secuencia - Parte III

## 1. Login y validacion del JWT en el gateway

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente (Postman)
    participant GW as api-gateway
    participant AU as auth-service
    participant DB as MongoDB bank_auth
    participant MS as microservicio destino

    C->>GW: POST /auth/login {username, password}
    Note over GW: ruta publica (permitAll)
    GW->>AU: POST /auth/login (lb://bank-auth-service)
    AU->>DB: findByUsername
    DB-->>AU: usuario con passwordHash BCrypt
    alt credenciales validas y usuario habilitado
        AU-->>GW: 200 {accessToken HS256, tokenType Bearer, expiresIn 3600}
        GW-->>C: 200 token
    else usuario o clave incorrectos
        AU-->>GW: 401 Invalid username or password
        GW-->>C: 401 (mensaje generico)
    end

    C->>GW: PUT /accounts/{id} con Authorization Bearer token
    GW->>GW: valida firma HS256 y expiracion (mismo JWT_SECRET)
    alt sin token, firma invalida o vencido
        GW-->>C: 401 Missing, invalid or expired token
    else rol USER en PUT o DELETE
        GW-->>C: 403 Insufficient role for this operation
    else rol suficiente
        GW->>GW: borra X-User-* y X-Customer-Id del cliente
        GW->>GW: agrega X-User-Id, X-User-Roles, X-Customer-Id, X-User-Phone
        GW->>MS: reenvia la peticion (lb://)
        MS-->>GW: respuesta
        GW-->>C: respuesta
    end
```

## 2. Propagacion de la deuda vencida

```mermaid
sequenceDiagram
    autonumber
    participant SCH as Scheduler diario (credit)
    participant CR as credit-service
    participant K as Kafka bank.debt.events
    participant AC as account-service
    participant DC as debit-card-service
    participant CC as credit-card-service
    actor C as Cliente

    SCH->>CR: scan (cron 01:00 o POST /admin/overdue-debts/scan)
    CR->>CR: creditos con dueAmount mayor a 0, vencidos y sin notificar
    CR->>K: DEBT_OVERDUE_DETECTED (key customerId)
    CR->>CR: overdueNotified = true
    par cada consumidor con su group-id
        K-->>AC: DETECTED, upsert en overdue_debts
    and
        K-->>DC: DETECTED, upsert en overdue_debts
    and
        K-->>CC: DETECTED, upsert en overdue_debts
    and
        K-->>CR: DETECTED, upsert en overdue_debts
    end

    C->>AC: POST /accounts (cliente moroso)
    AC->>AC: consulta local overdue_debts (sin REST)
    AC-->>C: 409 has an overdue debt and cannot acquire new products

    C->>CR: pago de la cuota (via transaction-service)
    CR->>CR: dueAmount llega a 0, overdueNotified = false
    CR->>K: DEBT_OVERDUE_CLEARED
    K-->>AC: CLEARED, borra el registro
    C->>AC: POST /accounts
    AC-->>C: 201
```

## 3. Pago con tarjeta de debito con fallback

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant DC as debit-card-service
    participant KR as Kafka operation-requests
    participant AC as account-service
    participant KS as Kafka operation-results
    participant TX as transaction-service

    C->>DC: POST /debit-cards/{id}/payments {amount 80}
    DC->>DC: pago PENDING, intentadas = [principal]
    DC->>KR: ACCOUNT_OPERATION_REQUESTED WITHDRAWAL principal (key accountId)
    DC-->>C: 202 {paymentId, status PENDING}
    KR-->>AC: peticion
    AC->>AC: saldo insuficiente en la principal
    AC->>KS: ACCOUNT_OPERATION_REJECTED INSUFFICIENT_FUNDS
    KS-->>DC: rechazo (enlazado por currentOperationId)
    DC->>DC: siguiente cuenta asociada, en orden
    DC->>KR: WITHDRAWAL segunda cuenta (nuevo operationId)
    KR-->>AC: peticion
    AC->>AC: aplica el retiro sin comision y registra el saldo diario
    AC->>KS: ACCOUNT_OPERATION_COMPLETED balanceAfter
    KS-->>DC: completado, pago COMPLETED, chargedAccountId
    KS-->>TX: completado, movimiento DEBIT_PAYMENT en productType DEBIT_CARD
    C->>DC: GET /debit-cards/{id}/payments/{paymentId}
    DC-->>C: 200 status COMPLETED
    Note over DC: si ninguna cuenta alcanza, el pago queda FAILED
```

## 4. Pago Yanki con tarjeta asociada

```mermaid
sequenceDiagram
    autonumber
    actor C as Emisor
    participant YK as yanki-service
    participant KR as Kafka operation-requests
    participant AC as account-service
    participant KS as Kafka operation-results
    participant KY as Kafka bank.yanki.events

    C->>YK: POST /yanki/payments {from, to, amount}
    YK->>YK: valida celulares distintos y monederos existentes
    YK->>YK: tarjeta del emisor ACTIVE en debit_cards_replica
    YK->>KR: WITHDRAWAL cuenta principal del emisor
    YK-->>C: 202 PENDING (paso DEBIT_REQUESTED)
    KR-->>AC: retiro
    AC->>KS: COMPLETED
    KS-->>YK: debito hecho
    alt receptor con tarjeta
        YK->>KR: DEPOSIT cuenta principal del receptor
        KR-->>AC: deposito
        AC->>KS: COMPLETED
        KS-->>YK: credito hecho
        YK->>YK: COMPLETED
    else receptor sin tarjeta
        YK->>YK: suma al saldo propio del receptor, COMPLETED
    end
    YK->>KY: YANKI_PAYMENT_COMPLETED
    Note over YK,AC: si el deposito al receptor se rechaza, YK pide un DEPOSIT de devolucion al emisor (COMPENSATING) y el pago queda FAILED
```

## 5. Pago Yanki sin tarjeta y compensacion

```mermaid
sequenceDiagram
    autonumber
    actor C as Emisor (sin tarjeta)
    participant YK as yanki-service
    participant DB as MongoDB bank_yanki
    participant KY as Kafka bank.yanki.events

    C->>YK: POST /yanki/payments {from, to, amount}
    YK->>DB: monedero emisor
    alt saldo propio insuficiente
        YK-->>C: 409 Insufficient wallet balance
    else saldo suficiente
        YK->>DB: descuenta el saldo del emisor
        YK->>DB: monedero receptor
        alt receptor sin tarjeta
            YK->>DB: suma al saldo del receptor
            YK-->>C: 202 COMPLETED
            YK->>KY: YANKI_PAYMENT_COMPLETED
        else tarjeta del receptor inactiva
            YK->>DB: devuelve el importe al emisor (compensacion)
            YK-->>C: 202 FAILED RECEIVER_CARD_NOT_ACTIVE
            YK->>KY: YANKI_PAYMENT_FAILED
        end
    end
    Note over YK: un scheduler marca FAILED los pagos PENDING con mas de 2 minutos y compensa si el debito ya se hizo
```

## 6. Pago de terceros

```mermaid
sequenceDiagram
    autonumber
    actor P as Pagador
    participant TX as transaction-service
    participant CR as credit-service
    participant CU as customer-service
    participant DB as MongoDB bank_transactions

    P->>TX: POST /transactions/payment {CREDIT, productId, amount, payerCustomerId}
    TX->>CR: GET /credits/{id} (circuit breaker)
    CR-->>TX: credito con customerId del titular
    alt payerCustomerId distinto del titular
        TX->>CU: GET /customers/{payerCustomerId} (circuit breaker)
        alt no existe
            CU-->>TX: 404
            TX-->>P: 404 Customer not found
        else existe
            CU-->>TX: 200
        end
    end
    TX->>CR: POST balance-operations PAYMENT
    CR->>CR: reduce saldo y dueAmount, CLEARED si salda la deuda vencida
    CR-->>TX: 200
    TX->>DB: PAYMENT con payerCustomerId y thirdParty
    TX-->>P: 201 {customerId titular, payerCustomerId, thirdParty true}
```
