# bank-config-server

Spring Cloud Config Server del sistema bancario. Sirve los `.yml` de `bank-config-repo`
a Eureka, al API Gateway y a los cinco microservicios de negocio. **Debe arrancar
antes que cualquier otro servicio.** No se registra en Eureka (es opcional en la
guia y Eureka mismo depende de el).

## Requisitos

- Java 17
- Maven 3.9+

No necesita MongoDB.

## Variables de entorno

| Variable | Por defecto | Descripcion |
|---|---|---|
| `CONFIG_REPO_PATH` | `../bank-config-repo` | Ruta a la carpeta con los `.yml` |

## Arranque

```bash
mvn clean package
mvn spring-boot:run
```

Queda escuchando en el puerto **8888**.

## Verificacion

```bash
curl http://localhost:8888/bank-customer-service/default
curl http://localhost:8888/bank-account-service/default
```

La respuesta debe incluir `propertySources` con los valores de
`application.yml` y del `.yml` del servicio.

## Orden de arranque del sistema

1. `bank-config-server` (este)
2. `bank-eureka-server`
3. `bank-customer-service`
4. `bank-credit-card-service` y `bank-credit-service`
5. `bank-account-service`
6. `bank-transaction-service`
7. `bank-api-gateway`

Incluye Actuator: `GET /actuator/health` lo usa el healthcheck de Docker.
