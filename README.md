# Proyecto Bancario — Partes I, II y III (proyecto final)

Sistema bancario basado en microservicios. Maneja clientes (personales y
empresariales, con perfiles VIP y PYME), productos pasivos (cuentas), productos
activos (creditos y tarjetas de credito), movimientos, transferencias, reportes y,
desde la Parte III, seguridad con JWT, eventos con Kafka, cache con Redis,
tarjetas de debito, deuda vencida, pago de terceros y el monedero movil Yanki. No
hay interfaz grafica: todo se verifica con Postman, entrando por el API Gateway.

## Repositorios

Cada microservicio es un proyecto independiente, con su propio `pom.xml` y sin
libreria comun. Son **11 microservicios**:

| Carpeta | Rol | Puerto | Base MongoDB |
|---|---|---|---|
| `bank-config-server` | Spring Cloud Config Server | 8888 | — |
| `bank-eureka-server` | Registro de servicios (Eureka) | 8761 | — |
| `bank-api-gateway` | Punto de entrada, **valida JWT y roles** | 8080 | — |
| `bank-customer-service` | Clientes; eventos; **cache Redis** | 8081 | `bank_customers` |
| `bank-account-service` | Cuentas; eventos; **operaciones por Kafka**; deuda vencida | 8082 | `bank_accounts` |
| `bank-credit-service` | Creditos; **vencimientos y deuda vencida** | 8083 | `bank_credits` |
| `bank-credit-card-service` | Tarjetas de credito; **vencimientos y deuda vencida** | 8084 | `bank_credit_cards` |
| `bank-transaction-service` | Movimientos, transferencias, reportes; **pago de terceros**; movimientos desde eventos | 8085 | `bank_transactions` |
| `bank-auth-service` | **Nuevo**: usuarios y emision de JWT | 8086 | `bank_auth` |
| `bank-debit-card-service` | **Nuevo**: tarjetas de debito y pagos con fallback | 8087 | `bank_debit_cards` |
| `bank-yanki-service` | **Nuevo**: monedero movil Yanki | 8088 | `bank_yanki` |
| `bank-config-repo` | No es un microservicio: los `.yml` del Config Server | — | — |
| `bank-docs` | No es un microservicio: draw.io, eventos, secuencias y Docker | — | — |
| `bank-postman` | No es un microservicio: colecciones, environment y scripts de prueba | — | — |

Infraestructura: **Kafka** (9092, KRaft) y **Redis** (6379), ademas de MongoDB.

## Stack

| Tecnologia | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.3.13 |
| Spring Cloud | 2023.0.6 (Config, Eureka, Gateway, LoadBalancer) |
| Spring Security | 6.3 (gateway: resource server reactivo; auth: BCrypt) |
| Spring Kafka | 3.2 (incluido en Boot 3.3) |
| Spring Data Redis | reactivo (Lettuce) |
| Nimbus JOSE + JWT | 9.37.3 (HS256) |
| Resilience4j | 2.2.0 |
| RxJava | 3.1.11 |
| Checkstyle / JaCoCo | 10.26.1 / 0.8.15 |
| Kafka / Redis (Docker) | `apache/kafka:3.9.0` / `redis:7.4-alpine` |

## Arquitectura

```
Postman ──JWT──► bank-api-gateway :8080 ──lb://──► microservicios
                   │ 401 sin token / invalido / vencido · 403 rol insuficiente
                   │ quita X-User-* del cliente y agrega X-User-Id, X-User-Roles...
                   ▼
            bank-auth-service (emite JWT)   Eureka :8761   Config Server :8888

REST (Partes I y II, circuit breaker + timeout 2 s):
  account ─► customer, credit-card · credit, credit-card ─► customer
  transaction ─► account, credit, credit-card, customer (pagador)

Kafka (Parte III, ver bank-docs/docs/events.md):
  customer ─ bank.customer.events ─────────────► debit-card
  account ── bank.account.events ──────────────► debit-card
  credit, credit-card ─ bank.debt.events ──────► account, credit, credit-card, debit-card
  debit-card ─ bank.debit-card.events ─────────► yanki
  debit-card, yanki ─ bank.account.operation-requests ─► account
  account ─ bank.account.operation-results ────► debit-card, yanki, transaction
  yanki ─ bank.yanki.events (informativo)

Redis: cache de clientes en customer-service.
```

Los servicios nuevos (auth, debit-card, yanki) **no llaman por REST** a otros
servicios: usan copias locales alimentadas por eventos. Todos los consumidores son
idempotentes (`processed_events`) y los mensajes fallidos van a `<topic>.DLT`.

## Requisitos para correr local

- Java 17+, Maven 3.9+
- MongoDB en `localhost:27017`
- Kafka y Redis: `docker compose -f bank-docs/docker/docker-compose.infra.yml up -d`
- Variables `JWT_SECRET` (32+ bytes), `ADMIN_USERNAME`, `ADMIN_PASSWORD` para
  `bank-auth-service` y el gateway

## Compilar y verificar todo

```bash
for s in bank-config-server bank-eureka-server; do (cd "$s" && mvn -B clean package) || break; done
for s in bank-api-gateway bank-customer-service bank-account-service bank-credit-service \
         bank-credit-card-service bank-transaction-service bank-auth-service \
         bank-debit-card-service bank-yanki-service; do
  (cd "$s" && mvn -B clean verify) || break   # Checkstyle + tests + JaCoCo
done
```

## Orden de arranque (local)

1. MongoDB, Kafka y Redis
2. `bank-config-server` (8888)
3. `bank-eureka-server` (8761)
4. `bank-customer-service`, `bank-auth-service`
5. `bank-credit-card-service`, `bank-credit-service`
6. `bank-account-service`
7. `bank-transaction-service`, `bank-debit-card-service`
8. `bank-yanki-service`
9. `bank-api-gateway` (8080)

## Docker

```bash
cd bank-docs/docker
cp .env.example .env    # cambiar los secretos
docker compose up -d --build
```

Solo se publica el puerto 8080 (gateway). Detalles en
[`bank-docs/docker/README.md`](bank-docs/docker/README.md).

## Seguridad

1. `POST /auth/login` con el admin (o un usuario registrado con
   `POST /auth/register`) devuelve `{ accessToken, tokenType: "Bearer", expiresIn }`.
2. Enviar `Authorization: Bearer <token>` en cada peticion.

| Ruta o metodo | Rol |
|---|---|
| `POST /auth/login`, `POST /auth/register`, health | Publico |
| `/auth/users/**`, `/admin/**` | ADMIN |
| `PUT`, `DELETE` | ADMIN |
| `GET`, `POST` | USER o ADMIN |

## Manejo de errores

Todos los servicios responden con el mismo cuerpo
(`timestamp, status, error, message, path`).

| Caso | Codigo |
|---|---|
| Creacion exitosa | 201 |
| Pago asincrono aceptado (debito, Yanki) | 202 |
| Lectura o actualizacion exitosa | 200 |
| Eliminacion exitosa | 204 |
| Validacion de entrada / parametro invalido | 400 |
| Sin token, token invalido o vencido; login invalido | 401 |
| Rol insuficiente; endpoint interno | 403 |
| Recurso inexistente | 404 |
| Regla de negocio violada (incluye deuda vencida) | 409 |
| Servicio remoto o Kafka no disponible | 503 |

## Patrones de diseno

| Patron | Donde |
|---|---|
| Strategy / Factory | Reglas por tipo de cuenta y perfil (`bank-account-service`) |
| Chain of Responsibility | Validaciones de apertura de cuenta, con la deuda vencida como primer eslabon |
| Decorator | `RemoteCallDecorator`: circuit breaker y timeout sobre WebClient |
| Adapter | `ReactorRxAdapter` en cada servicio |
| Builder | Lombok `@Builder` |
| Publish/Subscribe | Eventos de Kafka con sobre comun |
| Idempotent Consumer | `IdempotentEventProcessor` + `processed_events`; `processed_operations` en cuentas |
| Read model (copia local) | `overdue_debts`, `customers_replica`, `accounts_replica`, `debit_cards_replica` |
| Saga (coreografia) | Pago con tarjeta de debito con fallback de cuentas |
| Saga con compensacion | Pagos Yanki (y transferencias de la Parte II) |
| Cache-aside | `CustomerCache` (Redis) |
| Dead Letter Channel | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` |

## Pruebas y calidad

| Prueba | Resultado |
|---|---|
| Tests unitarios (Mockito) en los 9 proyectos con codigo de negocio | 387 OK |
| Cobertura de lineas por servicio | 86.5% a 96.8% (`service.impl` entre 95.5% y 100%) |
| Flujo Parte I por el gateway con JWT (`run-flow.sh`) | 120 OK |
| Flujo Parte II (`run-flow-part2.sh`) | 90 OK |
| Flujo Parte III (`run-flow-part3.sh`) | 130 OK |
| Coleccion Postman de la Parte III (`mini-runner.ps1`) | 59 OK |
| Todo lo anterior contra `docker compose` (14 contenedores, solo el 8080 publicado) | Mismos resultados; en la Parte I fallan a proposito los 3 checks del Config Server (puerto 8888 no publicado) |

Cada repo muestra su reporte de cobertura en `docs/coverage.png`.

## Documentacion

- Cada microservicio: `docs/openapi.yaml`, `docs/class-diagram.md`,
  `docs/coverage.png` y su `README.md`.
- [`bank-docs`](bank-docs/README.md): draw.io global, catalogo de eventos y
  diagramas de secuencia de la Parte III.
- [`bank-postman`](bank-postman/README.md): colecciones, environment `bank-local`
  y scripts equivalentes con curl.

## Funcionalidades no incluidas

Las Partes I, II y III estan implementadas completas. Lo siguiente queda fuera
porque la guia de la Parte III lo excluye explicitamente:

- Refresh tokens, recuperacion y revocacion de contrasenas.
- Recarga de saldo del monedero Yanki.
- Tarjetas de credito asociadas a Yanki.
- Cobro automatico de la comision de mantenimiento.
- Kafka exactly-once: se usa at-least-once con consumidores idempotentes.

### Limitaciones conocidas

- Si el deposito de un pago Yanki llega despues de que el pago vencio, queda para
  revision manual.
- Un evento de deuda saldada (`CLEARED`) que se pierda no se vuelve a publicar.
- `bank-auth-service` no valida que el `customerId` del usuario exista (no usa REST).
