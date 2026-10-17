# bank-customer-service — Diagramas de secuencia

## 1. Crear cliente

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CustomerController
    participant S as CustomerServiceImpl
    participant R as CustomerRepository
    participant DB as MongoDB (bank_customers)

    C->>CT: POST /customers (CustomerRequest)
    CT->>CT: @Valid
    alt datos invalidos
        CT-->>C: 400 Bad Request
    end
    CT->>S: create(request)
    S->>R: existsByDocumentNumber(documentNumber)
    R->>DB: count customers
    DB-->>R: resultado
    R-->>S: Mono<Boolean>
    alt documento ya registrado
        S-->>CT: BusinessRuleException
        CT-->>C: 409 Conflict
    else documento libre
        S->>S: CustomerMapper.toDocument (profile=STANDARD, createdAt)
        S->>R: save(customer)
        R->>DB: insert
        DB-->>R: documento con id
        R-->>S: Mono<Customer>
        S-->>CT: Single<CustomerResponse>
        CT-->>C: 201 Created
    end
```

## 2. Consultar cliente por id

Lo usan tambien `account-service`, `credit-service` y `credit-card-service`
para conocer el tipo de cliente.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API / servicio remoto
    participant CT as CustomerController
    participant S as CustomerServiceImpl
    participant R as CustomerRepository
    participant DB as MongoDB (bank_customers)

    C->>CT: GET /customers/{id}
    CT->>S: findById(id)
    S->>R: findById(id)
    R->>DB: find
    DB-->>R: documento o vacio
    R-->>S: Mono<Customer>
    alt no existe
        S-->>CT: ResourceNotFoundException
        CT-->>C: 404 Not Found
    else existe
        S-->>CT: Single<CustomerResponse>
        CT-->>C: 200 OK
    end
```

## 3. Actualizar cliente

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CustomerController
    participant S as CustomerServiceImpl
    participant R as CustomerRepository

    C->>CT: PUT /customers/{id} (CustomerRequest)
    CT->>S: update(id, request)
    S->>R: findById(id)
    alt no existe
        S-->>CT: ResourceNotFoundException
        CT-->>C: 404 Not Found
    end
    opt documentNumber cambio
        S->>R: existsByDocumentNumber(nuevo)
        alt ya pertenece a otro cliente
            S-->>CT: BusinessRuleException
            CT-->>C: 409 Conflict
        end
    end
    S->>S: CustomerMapper.merge(existing, request)
    S->>R: save(customer)
    R-->>S: Mono<Customer>
    S-->>CT: Single<CustomerResponse>
    CT-->>C: 200 OK
```

## 4. Eliminar cliente

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente API (Postman)
    participant CT as CustomerController
    participant S as CustomerServiceImpl
    participant R as CustomerRepository

    C->>CT: DELETE /customers/{id}
    CT->>S: delete(id)
    S->>R: findById(id)
    alt no existe
        S-->>CT: ResourceNotFoundException
        CT-->>C: 404 Not Found
    else existe
        S->>R: delete(customer)
        R-->>S: Mono<Void>
        S-->>CT: Completable
        CT-->>C: 204 No Content
    end
```
