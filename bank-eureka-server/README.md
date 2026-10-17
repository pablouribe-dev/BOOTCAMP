# bank-eureka-server

Registro de servicios (Netflix Eureka) del sistema bancario. Todos los
microservicios de negocio y el API Gateway se registran aqui con su
`spring.application.name` y se encuentran entre si por ese nombre.

## Requisitos

- Java 17
- Maven 3.9+
- `bank-config-server` arriba en el puerto 8888

## Variables de entorno

| Variable | Por defecto | Descripcion |
|---|---|---|
| `CONFIG_SERVER_URL` | `http://localhost:8888` | URL del Config Server |
| `EUREKA_HOSTNAME` | `localhost` | Hostname con el que se anuncia (en Docker: `eureka-server`) |

El puerto (8761) y los flags `register-with-eureka: false` y
`fetch-registry: false` llegan desde `bank-config-repo/bank-eureka-server.yml`.

## Arranque

```bash
mvn clean package
mvn spring-boot:run
```

Panel de control: http://localhost:8761

Debe arrancar despues del Config Server y antes que el resto.

## Servicios que deben aparecer

`BANK-CUSTOMER-SERVICE`, `BANK-ACCOUNT-SERVICE`, `BANK-CREDIT-SERVICE`,
`BANK-CREDIT-CARD-SERVICE`, `BANK-TRANSACTION-SERVICE` y `BANK-API-GATEWAY`.

Tambien se puede consultar por API:

```bash
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

## Notas

- El servidor no se registra a si mismo.
- `enable-self-preservation: false`: en desarrollo los servicios se reinician
  seguido y asi Eureka limpia rapido las instancias caidas.
- Los clientes registran su IP (`prefer-ip-address: true`), lo que funciona igual
  en local y en Docker.
