package com.bank.customer.cache;

import com.bank.customer.adapter.ReactorRxAdapter;
import com.bank.customer.config.CacheProperties;
import com.bank.customer.model.Customer;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Cache-aside de clientes (datos maestros) en Redis.
 *
 * <ul>
 *   <li>Claves: {@code customer:id:{id}} y {@code customer:doc:{documentNumber}}.</li>
 *   <li>TTL desde configuracion ({@code bank.cache.ttl}).</li>
 *   <li>Si Redis no responde, nunca falla: registra WARN y se comporta como
 *       una cache vacia, de modo que el servicio sigue contra MongoDB.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerCache {

    /** Prefijo de la clave por id. */
    static final String ID_KEY = "customer:id:";

    /** Prefijo de la clave por numero de documento. */
    static final String DOC_KEY = "customer:doc:";

    private final ReactiveRedisTemplate<String, Customer> customerRedisTemplate;
    private final CacheProperties cacheProperties;

    /**
     * Busca un cliente por id.
     *
     * @param id identificador del cliente
     * @return el cliente cacheado, o vacio si no esta o Redis no responde
     */
    public Maybe<Customer> findById(String id) {
        return get(ID_KEY + id);
    }

    /**
     * Busca un cliente por numero de documento.
     *
     * @param documentNumber numero de documento
     * @return el cliente cacheado, o vacio si no esta o Redis no responde
     */
    public Maybe<Customer> findByDocumentNumber(String documentNumber) {
        return get(DOC_KEY + documentNumber);
    }

    /**
     * Guarda el cliente bajo sus dos claves.
     *
     * @param customer cliente a cachear
     * @return completable que siempre termina bien
     */
    public Completable put(Customer customer) {
        Mono<Boolean> write = customerRedisTemplate.opsForValue()
                .set(ID_KEY + customer.getId(), customer, cacheProperties.getTtl())
                .then(customerRedisTemplate.opsForValue()
                        .set(DOC_KEY + customer.getDocumentNumber(), customer, cacheProperties.getTtl()));
        return ReactorRxAdapter.monoToCompletable(guard(write, "put customer " + customer.getId()))
                .doOnComplete(() -> log.debug("Customer {} cached", customer.getId()));
    }

    /**
     * Invalida la clave por id y las claves de los numeros de documento dados
     * (por ejemplo el anterior y el nuevo, si cambio).
     *
     * @param id              identificador del cliente
     * @param documentNumbers numeros de documento cuyas claves se borran
     * @return completable que siempre termina bien
     */
    public Completable evict(String id, String... documentNumbers) {
        String[] keys = Arrays.stream(documentNumbers)
                .filter(Objects::nonNull)
                .map(document -> DOC_KEY + document)
                .toArray(String[]::new);
        Mono<Long> delete = customerRedisTemplate.delete(ID_KEY + id)
                .flatMap(deleted -> keys.length == 0 ? Mono.just(deleted) : customerRedisTemplate.delete(keys));
        return ReactorRxAdapter.monoToCompletable(guard(delete, "evict customer " + id))
                .doOnComplete(() -> log.debug("Customer {} evicted from cache", id));
    }

    /**
     * Lectura protegida de una clave.
     *
     * @param key clave a leer
     * @return el valor, o vacio
     */
    private Maybe<Customer> get(String key) {
        return ReactorRxAdapter.monoToMaybe(guard(customerRedisTemplate.opsForValue().get(key), "get " + key))
                .doOnSuccess(customer -> log.debug("Cache hit for {}", key));
    }

    /**
     * Aplica el timeout configurado y convierte cualquier error de Redis en
     * un resultado vacio con un WARN.
     *
     * @param operation operacion contra Redis
     * @param action    descripcion para el log
     * @param <T>       tipo del resultado
     * @return la operacion protegida
     */
    private <T> Mono<T> guard(Mono<T> operation, String action) {
        return operation.timeout(cacheProperties.getTimeout())
                .onErrorResume(ex -> {
                    log.warn("Redis unavailable ({}): {}. Falling back to MongoDB", action, ex.toString());
                    return Mono.empty();
                });
    }
}
