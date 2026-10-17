# bank-docs

Documentacion global del sistema bancario. **No es un microservicio.**

| Archivo | Contenido |
|---|---|
| [`architecture.drawio`](architecture.drawio) | Diagrama global (Parte III): gateway con JWT, 11 microservicios, Kafka con sus topics, Redis, Eureka, Config Server y bases. Abrir con [diagrams.net](https://app.diagrams.net) |
| [`docs/events.md`](docs/events.md) | Catalogo de eventos: cada topic, quien publica y consume, y ejemplos de payload |
| [`docs/sequence-diagrams-part3.md`](docs/sequence-diagrams-part3.md) | Secuencias (Mermaid): login y validacion en el gateway, deuda vencida, pago de debito con fallback, Yanki con y sin tarjeta, pago de terceros |
| [`docker/`](docker/README.md) | `docker-compose.yml` del sistema completo, `docker-compose.infra.yml` (Kafka y Redis para desarrollo) y `.env.example` |
| [`postman/`](postman/README.md) | Desde la Parte III, las colecciones viven en [`bank-postman`](../bank-postman/README.md) |

Cada microservicio tiene en su `docs/` su `openapi.yaml`, `class-diagram.md`,
`sequence-diagrams.md` (Partes I y II) y `coverage.png`.
