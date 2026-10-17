# bank-config-repo

Repositorio de configuracion que sirve el `bank-config-server`. **No es un microservicio**:
aqui solo viven archivos `.yml`.

## Contenido

| Archivo | A quien aplica |
|---|---|
| `application.yml` | Todos (Eureka, Resilience4j, Actuator, logging) |
| `bank-eureka-server.yml` | `bank-eureka-server` (puerto 8761, no se registra a si mismo) |
| `bank-api-gateway.yml` | `bank-api-gateway` (puerto 8080, rutas `lb://`, rutas bloqueadas) |
| `bank-customer-service.yml` | `bank-customer-service` |
| `bank-account-service.yml` | `bank-account-service` (minimos de apertura, comisiones, VIP, PYME) |
| `bank-credit-service.yml` | `bank-credit-service` |
| `bank-credit-card-service.yml` | `bank-credit-card-service` |
| `bank-transaction-service.yml` | `bank-transaction-service` (tipos del reporte de ultimos movimientos) |

El nombre del archivo debe coincidir con el `spring.application.name` del servicio.

## Configuracion comun (`application.yml`)

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
resilience4j:
  timelimiter:
    configs:
      default:
        timeout-duration: 2s
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

Cada cliente WebClient crea una instancia de circuit breaker y de time limiter por
servicio destino, que hereda esta configuracion `default`.

## Variables de entorno

Los valores `${VAR:valor}` permiten que Docker los sobrescriba sin tocar el codigo:

| Variable | Donde | Por defecto |
|---|---|---|
| `EUREKA_URL` | `application.yml` | `http://localhost:8761/eureka` |
| `MONGO_URI` | cada `bank-<servicio>.yml` | `mongodb://localhost:27017/bank_<servicio>` |
| `CUSTOMER_SERVICE_URL`, `ACCOUNT_SERVICE_URL`, ... | servicios con clientes | nombre de Eureka, p. ej. `http://bank-customer-service` |
| `EUREKA_HOSTNAME` | `bank-eureka-server.yml` | `localhost` |

## Como lo lee el Config Server

El `bank-config-server` corre con el perfil `native` y apunta a esta carpeta mediante
la propiedad `CONFIG_REPO_PATH` (por defecto `../bank-config-repo`; en Docker se
monta en `/config-repo`).

Para verificar que sirve bien un archivo, con el Config Server arriba:

```bash
curl http://localhost:8888/bank-account-service/default
```

## Que NO debe estar en el codigo

Puertos, URI de MongoDB, URLs (nombres de Eureka) de los otros servicios, rutas del
gateway y limites de negocio (minimos de apertura, comisiones, limites de
movimientos, promedio minimo VIP, maximos por tipo de cliente) viven aqui.

---

# Parte III

## Archivos nuevos

| Archivo | A quien aplica |
|---|---|
| `bank-auth-service.yml` | Puerto 8086, `bank.security.jwt` (secreto por env), administrador inicial |
| `bank-debit-card-service.yml` | Puerto 8087, prefijo de tarjeta, topics publicados y consumidos |
| `bank-yanki-service.yml` | Puerto 8088, timeout de pagos colgados, topics |

## Configuracion comun nueva (`application.yml`)

- `spring.kafka.*`: `KAFKA_BOOTSTRAP_SERVERS`, serializador JSON sin type headers,
  `ErrorHandlingDeserializer` + `JsonDeserializer` con
  `spring.json.use.type.headers=false`, `group-id = spring.application.name`,
  `auto-offset-reset: earliest`, `metadata.max.age.ms: 30000`.
- `bank.kafka`: particiones (3), replicas (1), reintentos (1 s, x2, 3 veces) y el
  catalogo de nombres de topics. Cada servicio declara en su archivo los topics
  que publica (`published`) y consume (`consumed`) y el tipo por defecto del sobre
  (`spring.json.value.default.type`).

## Cambios en archivos existentes

| Archivo | Que se agrego |
|---|---|
| `bank-customer-service.yml` | Redis (`REDIS_HOST`, `REDIS_PORT`, timeout 500 ms), `bank.cache` (TTL 10 min), publica `customer-events` |
| `bank-account-service.yml` | Publica `account-events` y `operation-results`; consume `debt-events` y `operation-requests` |
| `bank-credit-service.yml`, `bank-credit-card-service.yml` | `bank.debts.overdue-scan-cron`, publican y consumen `debt-events` |
| `bank-transaction-service.yml` | `customer-service-url`, `DEBIT_CARD` en el reporte de ultimos movimientos, consume `operation-results` |
| `bank-api-gateway.yml` | Rutas de auth, debit-cards, yanki y `/admin/<servicio>/**`, `bank.security.jwt.secret` |

## Variables de entorno nuevas

| Variable | Donde | Por defecto |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `application.yml` | `localhost:9092` |
| `REDIS_HOST` / `REDIS_PORT` | customer | `localhost` / `6379` |
| `JWT_SECRET` | auth, gateway | **sin valor**: obligatoria |
| `JWT_EXPIRATION` | auth | `60m` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | auth | sin valor (sin ellas no se crea el admin inicial) |
| `OVERDUE_SCAN_CRON` | credit, credit-card | `0 0 1 * * *` |
| `YANKI_PAYMENT_TIMEOUT` | yanki | `2m` |

Los secretos no tienen valor por defecto en el repositorio.
