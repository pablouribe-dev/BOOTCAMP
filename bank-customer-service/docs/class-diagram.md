# bank-customer-service — Diagrama de clases

```mermaid
classDiagram
    direction LR

    class CustomerController {
        -CustomerService customerService
        +create(CustomerRequest) Single~CustomerResponse~
        +findAll() Flowable~CustomerResponse~
        +findById(String) Single~CustomerResponse~
        +update(String, CustomerRequest) Single~CustomerResponse~
        +delete(String) Completable
    }

    class CustomerService {
        <<interface>>
        +create(CustomerRequest) Single~CustomerResponse~
        +findAll() Flowable~CustomerResponse~
        +findById(String) Single~CustomerResponse~
        +update(String, CustomerRequest) Single~CustomerResponse~
        +delete(String) Completable
    }

    class CustomerServiceImpl {
        -CustomerRepository customerRepository
        -CustomerMapper customerMapper
        -findOrFail(String) Single~Customer~
        -ensureDocumentNumberIsFree(Customer, CustomerRequest) Completable
    }

    class CustomerRepository {
        <<interface>>
        +existsByDocumentNumber(String) Mono~Boolean~
        +findByDocumentNumber(String) Mono~Customer~
    }

    class ReactiveMongoRepository {
        <<interface>>
    }

    class CustomerMapper {
        +toDocument(CustomerRequest) Customer
        +merge(Customer, CustomerRequest) Customer
        +toResponse(Customer) CustomerResponse
    }

    class Customer {
        <<Document customers>>
        -String id
        -CustomerType type
        -DocumentType documentType
        -String documentNumber
        -String name
        -String email
        -String phoneNumber
        -String address
        -CustomerProfile profile
        -LocalDateTime createdAt
    }

    class CustomerRequest {
        -CustomerType type
        -DocumentType documentType
        -String documentNumber
        -String name
        -String email
        -String phoneNumber
        -String address
        -CustomerProfile profile
    }

    class CustomerResponse {
        -String id
        -CustomerType type
        -DocumentType documentType
        -String documentNumber
        -String name
        -String email
        -String phoneNumber
        -String address
        -CustomerProfile profile
        -LocalDateTime createdAt
    }

    class CustomerType {
        <<enumeration>>
        PERSONAL
        BUSINESS
    }

    class DocumentType {
        <<enumeration>>
        DNI
        CEX
        PASSPORT
        RUC
    }

    class CustomerProfile {
        <<enumeration>>
        STANDARD
    }

    class GlobalExceptionHandler {
        +handleResourceNotFound() ResponseEntity~ErrorResponse~
        +handleBusinessRule() ResponseEntity~ErrorResponse~
        +handleDuplicateKey() ResponseEntity~ErrorResponse~
        +handleValidation() ResponseEntity~ErrorResponse~
        +handleInput() ResponseEntity~ErrorResponse~
        +handleUnexpected() ResponseEntity~ErrorResponse~
    }

    class ErrorResponse {
        -LocalDateTime timestamp
        -int status
        -String error
        -String message
        -String path
    }

    class ResourceNotFoundException
    class BusinessRuleException
    class RuntimeException

    CustomerController --> CustomerService
    CustomerServiceImpl ..|> CustomerService
    CustomerServiceImpl --> CustomerRepository
    CustomerServiceImpl --> CustomerMapper
    CustomerRepository --|> ReactiveMongoRepository
    CustomerMapper ..> Customer
    CustomerMapper ..> CustomerRequest
    CustomerMapper ..> CustomerResponse
    Customer --> CustomerType
    Customer --> DocumentType
    Customer --> CustomerProfile
    GlobalExceptionHandler ..> ErrorResponse
    ResourceNotFoundException --|> RuntimeException
    BusinessRuleException --|> RuntimeException
    CustomerServiceImpl ..> ResourceNotFoundException : lanza
    CustomerServiceImpl ..> BusinessRuleException : lanza
```

## Parte II: perfiles y adaptador Reactor - RxJava

```mermaid
classDiagram
    direction LR

    class CustomerProfile {
        <<enumeration>>
        STANDARD
        VIP
        PYME
        -CustomerType requiredType
        +isAllowedFor(CustomerType) boolean
    }
    class CustomerServiceImpl {
        -ensureProfileMatchesType(CustomerType, CustomerProfile) Completable
    }
    class ReactorRxAdapter {
        <<Adapter>>
        +monoToSingle(Mono) Single
        +monoToMaybe(Mono) Maybe
        +fluxToFlowable(Flux) Flowable
        +monoToCompletable(Mono) Completable
    }

    CustomerProfile --> CustomerType : VIP solo PERSONAL, PYME solo BUSINESS
    CustomerServiceImpl ..> CustomerProfile : valida al crear y actualizar
    CustomerServiceImpl ..> ReactorRxAdapter : Mono/Flux a RxJava 3
```

El servicio se registra en Eureka como `bank-customer-service`. No llama a otros
servicios, por eso no tiene capa `client/` ni circuit breaker.

## Parte III: eventos y cache Redis

```mermaid
classDiagram
    direction LR
    class CustomerServiceImpl
    class CustomerCache {
        +findById(id) Maybe~Customer~
        +findByDocumentNumber(doc) Maybe~Customer~
        +put(Customer) Completable
        +evict(id, documentNumbers) Completable
    }
    class CustomerEventPublisher {
        +publishUpserted(Customer) Completable
        +publishDeleted(customerId) Completable
    }
    class EventRepublishServiceImpl {
        +republishAll() Single~RepublishResponse~
    }
    class AdminController
    class EventPublisher {
        +publish(topic, key, eventType, payload) Completable
    }
    class ReactiveRedisTemplate
    class KafkaConfig {
        +bankTopics() NewTopics
        +kafkaErrorHandler(template) DefaultErrorHandler
    }
    CustomerServiceImpl --> CustomerCache : cache-aside
    CustomerServiceImpl --> CustomerEventPublisher
    AdminController --> EventRepublishServiceImpl
    EventRepublishServiceImpl --> CustomerEventPublisher
    CustomerEventPublisher --> EventPublisher
    CustomerCache --> ReactiveRedisTemplate
```

`CustomerCache` nunca falla: si Redis no responde (timeout de 500 ms), registra
WARN y el servicio sigue contra MongoDB. Un fallo al publicar no revierte la
operacion (ERROR en el log; se recupera con `POST /admin/events/republish`).
