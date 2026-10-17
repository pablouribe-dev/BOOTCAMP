# Catalogo de eventos (Parte III)

Todos los mensajes viajan por Kafka con el mismo sobre JSON:

```json
{
  "eventId": "a0831bd1-3e02-4485-ad67-7f15748d3d7f",
  "eventType": "CUSTOMER_UPSERTED",
  "occurredAt": "2026-10-17T05:20:05.715610400Z",
  "payload": { }
}
```

## Reglas comunes

| Regla | Como se cumple |
|---|---|
| Clave del mensaje | Id del agregado (`customerId`, `accountId`, `cardId`, id del pago). Las peticiones de operacion usan `accountId`: las operaciones de una cuenta se procesan una tras otra. |
| Entrega al menos una vez | Cada consumidor guarda los `eventId` procesados en `processed_events` (es el `_id`, unico) y descarta repetidos. `bank-account-service` ademas es idempotente por `operationId` (`processed_operations`) y `bank-transaction-service` por `operationId` del movimiento. |
| Snapshots | `..._UPSERTED` lleva todos los campos y `..._DELETED` el id: repetirlos es inofensivo. |
| Sin libreria compartida | Cada servicio tiene sus clases del sobre y de los payloads. Deserializacion con tipo por defecto y `spring.json.use.type.headers=false`. |
| Errores de consumo | 3 reintentos con backoff exponencial (1 s, x2) y luego `<topic>.DLT`, registrado con nivel ERROR. Un mensaje ilegible va directo al DLT. |
| Creacion de topics | El servicio que publica crea el topic (3 particiones, 1 replica). Los consumidores tambien declaran los que consumen y su `.DLT`, para no depender del orden de arranque. |

## Topics

| Topic | Publica | Consume (group-id = nombre del servicio) | Eventos |
|---|---|---|---|
| `bank.customer.events` | customer | debit-card | `CUSTOMER_UPSERTED`, `CUSTOMER_DELETED` |
| `bank.account.events` | account | debit-card | `ACCOUNT_UPSERTED`, `ACCOUNT_DELETED` |
| `bank.debt.events` | credit, credit-card | account, credit, credit-card, debit-card | `DEBT_OVERDUE_DETECTED`, `DEBT_OVERDUE_CLEARED` |
| `bank.debit-card.events` | debit-card | yanki | `DEBIT_CARD_UPSERTED`, `DEBIT_CARD_DELETED` |
| `bank.account.operation-requests` | debit-card, yanki | account | `ACCOUNT_OPERATION_REQUESTED` |
| `bank.account.operation-results` | account | debit-card, yanki, transaction | `ACCOUNT_OPERATION_COMPLETED`, `ACCOUNT_OPERATION_REJECTED` |
| `bank.yanki.events` | yanki | (informativo) | `YANKI_PAYMENT_COMPLETED`, `YANKI_PAYMENT_FAILED` |

Cada topic consumido tiene su `<topic>.DLT`.

## Payloads

### `bank.customer.events`

```json
{ "eventType": "CUSTOMER_UPSERTED",
  "payload": { "customerId": "6ad30585e4f1e12a4386be41", "type": "PERSONAL", "profile": "STANDARD",
               "documentType": "DNI", "documentNumber": "70000001" } }
```

`CUSTOMER_DELETED`: `{ "customerId": "..." }`. Se publican al crear, actualizar y
eliminar un cliente, y con `POST /admin/events/republish`.

### `bank.account.events`

```json
{ "eventType": "ACCOUNT_UPSERTED",
  "payload": { "accountId": "6ad38ad2710e9957a0b6fd16", "customerId": "6ad38ad2c9b44d590d953d69",
               "accountType": "SAVINGS", "accountNumber": "22054433643866", "profile": "STANDARD" } }
```

`ACCOUNT_DELETED`: `{ "accountId": "...", "customerId": "..." }`. Al recibirlo,
debit-card quita la cuenta de sus tarjetas (si era la principal pasa a serlo la
siguiente; sin cuentas la tarjeta queda `INACTIVE`).

### `bank.debt.events`

```json
{ "eventType": "DEBT_OVERDUE_DETECTED",
  "payload": { "customerId": "6ad3883bc9b44d590d953d64", "productType": "CREDIT",
               "productId": "6ad3883dd8a2f1b1a7c0e011", "dueDate": "2026-10-16", "dueAmount": 150 } }
```

`DEBT_OVERDUE_CLEARED`: `{ "customerId", "productType", "productId" }`.
`DETECTED` lo publica el proceso diario (cron `bank.debts.overdue-scan-cron`, o
`POST /admin/overdue-debts/scan`) cuando `dueAmount > 0` y el vencimiento ya
paso. `CLEARED` se publica cuando un pago deja `dueAmount` en 0 (o un PUT mueve
el vencimiento). Los consumidores mantienen `overdue_debts` y rechazan con 409
la adquisicion de productos de ese cliente.

### `bank.debit-card.events`

```json
{ "eventType": "DEBIT_CARD_UPSERTED",
  "payload": { "cardId": "6ad38ad9a5effb47307d298c", "cardNumber": "5555042871904413",
               "customerId": "6ad38ad2c9b44d590d953d69",
               "accountIds": ["6ad38ad2710e9957a0b6fd16", "6ad38ad3710e9957a0b6fd18"],
               "mainAccountId": "6ad38ad2710e9957a0b6fd16", "status": "ACTIVE" } }
```

### `bank.account.operation-requests`

```json
{ "eventType": "ACCOUNT_OPERATION_REQUESTED",
  "payload": { "operationId": "0d6c2b4e-...", "accountId": "6ad38ad2710e9957a0b6fd16",
               "operation": "WITHDRAWAL", "amount": 80, "origin": "DEBIT_CARD",
               "reference": "6ad38ad9a5effb47307d298c" } }
```

`origin`: `DEBIT_CARD` (`reference` = id de la tarjeta) o `YANKI` (`reference` =
id del pago Yanki). `operation`: `DEPOSIT` o `WITHDRAWAL`.

### `bank.account.operation-results`

```json
{ "eventType": "ACCOUNT_OPERATION_COMPLETED",
  "payload": { "operationId": "0d6c2b4e-...", "accountId": "6ad38ad3710e9957a0b6fd18",
               "customerId": "6ad38ad2c9b44d590d953d69", "operation": "WITHDRAWAL", "amount": 80,
               "balanceAfter": 420, "origin": "DEBIT_CARD", "reference": "6ad38ad9a5effb47307d298c" } }
```

```json
{ "eventType": "ACCOUNT_OPERATION_REJECTED",
  "payload": { "operationId": "9a1f...", "accountId": "6ad38ad2710e9957a0b6fd16",
               "origin": "DEBIT_CARD", "reference": "6ad38ad9a5effb47307d298c",
               "reason": "INSUFFICIENT_FUNDS" } }
```

`reason`: `INSUFFICIENT_FUNDS` o `ACCOUNT_NOT_FOUND`. Las operaciones por eventos
no cobran comision ni cuentan para los maximos de movimientos.

### `bank.yanki.events`

```json
{ "eventType": "YANKI_PAYMENT_COMPLETED",
  "payload": { "paymentId": "6ad39001b1c2d3e4f5a6b7c8", "fromPhoneNumber": "912345678",
               "toPhoneNumber": "987654321", "amount": 50, "status": "COMPLETED" } }
```

`YANKI_PAYMENT_FAILED` agrega `failureReason` (`INSUFFICIENT_FUNDS`,
`RECEIVER_CARD_NOT_ACTIVE`, `TIMEOUT`, ...).
