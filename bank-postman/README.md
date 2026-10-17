# bank-postman

Proyectos Postman del sistema bancario (entregable de la Parte III). Todo se
prueba a traves del API Gateway (`http://localhost:8080`) con JWT.

## Contenido

| Ruta | Que es |
|---|---|
| `environments/bank-local.postman_environment.json` | Environment `bank-local`: `baseUrl=http://localhost:8080`, `adminUsername`, `adminPassword` |
| `collections/bank-part-3.postman_collection.json` | **Parte III**: una carpeta por microservicio y un flujo de punta a punta (59 requests) |
| `collections/bank-parts-1-2.postman_collection.json` | Partes I y II (108 requests), ahora con login de ADMIN al inicio |
| `scripts/run-flow.sh` | Equivalente con curl de la coleccion de la Parte I |
| `scripts/run-flow-part2.sh` | Equivalente con curl de la Parte II |
| `scripts/run-flow-part3.sh` | Equivalente con curl de la Parte III (130 verificaciones) |
| `tools/generate-collection.ps1` | Genera la coleccion de la Parte III |
| `tools/mini-runner.ps1` | Ejecuta una coleccion sin Postman ni Newman (verifica codigos, textos y variables) |
| `tools/add-auth-parts-1-2.ps1` | Agrega el login JWT a la coleccion de las Partes I y II |

## Como usar en Postman

1. Importar el environment `bank-local` y las dos colecciones.
2. Seleccionar el environment `bank-local`. La contrasena del admin debe ser la
   misma de `ADMIN_PASSWORD` con la que arranco `bank-auth-service`.
3. Ejecutar la coleccion con el **Collection Runner, en orden**.

El primer request (`00 - Login / Login ADMIN`) guarda el token en la variable
`token` y la coleccion lo envia como `Authorization: Bearer {{token}}` (auth a
nivel de coleccion). Los requests de "rol insuficiente" usan el token de un
USER (`userToken`) y los de "sin token" desactivan la auth.

Cada request tiene assertions: codigo HTTP y, segun el caso, campos y mensajes.
Los requests que dependen de un evento de Kafka esperan 3 s en el pre-request.

## Coleccion de la Parte III

| Carpeta | Que prueba |
|---|---|
| 00 - Login | Login ADMIN (guarda el token), registro y login de un USER |
| 01 - auth-service | Login invalido (401 generico), CRUD de usuarios (ADMIN), 403 para USER |
| 02 - customer-service | Alta, busqueda por documento e id (cache Redis), republicacion de snapshots |
| 03 - account-service | Cuentas para el resto del flujo, republicacion de snapshots |
| 04 - debit-card-service | Tarjeta con dos cuentas, pago con **fallback** a la segunda cuenta, saldos, cambio de principal |
| 05 - transaction-service | Ultimos movimientos de la tarjeta de debito, **pago de terceros**, pagador inexistente |
| 06 - credit y credit-card | Vencimiento por PUT, deteccion de deuda vencida, 409 al adquirir, liberacion al pagar |
| 07 - yanki-service | Monederos, asociacion a tarjeta, pago **con tarjeta** y **sin tarjeta**, saldo insuficiente |
| 99 - Flujo de punta a punta | Sin token (401), rol insuficiente (403), endpoint interno (403), moroso no abre cuenta, debito fallido, Yanki al instante y al mismo celular |

## Sin Postman

```bash
# Con todo el sistema arriba (local o Docker):
bash scripts/run-flow.sh            # Parte I (directo a los servicios; GATEWAY=http://localhost:8080 para ir por el gateway)
bash scripts/run-flow-part2.sh      # Parte II
bash scripts/run-flow-part3.sh      # Parte III

# Verificar la coleccion de la Parte III sin Postman (Windows PowerShell):
powershell -File tools/mini-runner.ps1 collections/bank-part-3.postman_collection.json environments/bank-local.postman_environment.json
```

Variables de los scripts: `GATEWAY` (por defecto `http://localhost:8080`),
`ADMIN_USERNAME`, `ADMIN_PASSWORD` y `EVENT_WAIT` (segundos de espera para los
eventos, 3 por defecto).

## Resultados de la ultima corrida

| Prueba | Resultado |
|---|---|
| `run-flow.sh` (Parte I, por el gateway con JWT) | 120 OK |
| `run-flow-part2.sh` | 90 OK |
| `run-flow-part3.sh` | 130 OK |
| Coleccion de la Parte III con `mini-runner.ps1` | 59 OK |
