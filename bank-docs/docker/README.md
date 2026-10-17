# Docker

## Sistema completo (`docker-compose.yml`)

Levanta MongoDB, Kafka (KRaft, sin Zookeeper), Redis, Config Server, Eureka y los
11 microservicios.

```bash
cp .env.example .env        # y cambiar JWT_SECRET, ADMIN_USERNAME y ADMIN_PASSWORD
docker compose up -d --build
docker compose ps           # esperar a que todos esten "healthy" (varios minutos la primera vez)
```

- **Unico puerto publicado: el API Gateway en http://localhost:8080.** Los
  microservicios, Eureka, Kafka, Redis y MongoDB solo son accesibles dentro de la
  red de Docker. Por eso la validacion del JWT solo en el gateway es efectiva.
- Secretos en `.env`, que **no se sube** al repositorio (`.gitignore`). En el repo
  va `.env.example`. Si falta alguna variable obligatoria, compose no arranca.

Para bajar todo (los datos de Mongo y Kafka quedan en volumenes):

```bash
docker compose down
```

### Orden de arranque

`depends_on` con `condition: service_healthy`:

```
mongo, kafka, redis, config-server
  └─ eureka-server
       ├─ customer-service ─┬─ credit-card-service ─┐
       │                    └─ credit-service       ├─ account-service ─ transaction-service ─┐
       │                                            │                └─ debit-card-service ─ yanki-service
       └─ auth-service ─────────────────────────────────────────────────────────────────────── api-gateway
```

### Variables

| Variable | Valor en Docker |
|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `EUREKA_URL` | `http://eureka-server:8761/eureka` |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` |
| `MONGO_URI` | `mongodb://mongo:27017/bank_<servicio>` |
| `REDIS_HOST` / `REDIS_PORT` | `redis` / `6379` (customer) |
| `JWT_SECRET` | desde `.env` (auth y gateway) |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | desde `.env` (auth) |
| `TZ` | `${BANK_TZ:-America/Lima}` |

`TZ` define que dia es "hoy" (dia del plazo fijo, mes de las comisiones, reportes
del dia, deudas vencidas). Sin ella los contenedores usan UTC.

Kafka crea los topics con 3 particiones (`KAFKA_NUM_PARTITIONS`); ademas cada
servicio declara los que publica y consume. Redis corre sin persistencia: al
reiniciarse arranca vacio y nunca devuelve datos viejos.

## Solo infraestructura para desarrollo (`docker-compose.infra.yml`)

Kafka (9092) y Redis (6379) con puertos publicados, para correr los servicios
desde el IDE o con `java -jar` (MongoDB el de la PC):

```bash
docker compose -f docker-compose.infra.yml up -d
```

## Imagenes

Cada repo tiene su `Dockerfile` multi-stage: compila con
`maven:3.9.9-eclipse-temurin-17` (Checkstyle incluido) y ejecuta el jar sobre
`eclipse-temurin:17-jre-alpine` con un usuario sin privilegios.

## Pruebas contra Docker

```bash
cd ../../bank-postman/scripts
ADMIN_PASSWORD='<la del .env>' GATEWAY=http://localhost:8080 bash run-flow.sh
ADMIN_PASSWORD='<la del .env>' bash run-flow-part2.sh
ADMIN_PASSWORD='<la del .env>' bash run-flow-part3.sh
```

En Docker fallan a proposito los 3 checks de `00 - Config Server` de la Parte I,
porque el puerto 8888 no se publica. Para ver la configuracion servida:

```bash
docker compose exec eureka-server wget -qO- http://config-server:8888/bank-customer-service/default
```
