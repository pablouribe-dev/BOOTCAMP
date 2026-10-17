package com.bank.customer.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.customer.config.CacheProperties;
import com.bank.customer.model.Customer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CustomerCacheTest {

    @Mock
    private ReactiveRedisTemplate<String, Customer> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Customer> valueOperations;

    private CustomerCache cache;

    private final Customer customer = Customer.builder().id("c1").documentNumber("77").build();

    @BeforeEach
    void setUp() {
        CacheProperties properties = new CacheProperties();
        properties.setTtl(Duration.ofMinutes(10));
        properties.setTimeout(Duration.ofMillis(200));
        cache = new CustomerCache(redisTemplate, properties);
    }

    @Test
    void readsByIdAndByDocumentWithTheirOwnKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("customer:id:c1")).thenReturn(Mono.just(customer));
        when(valueOperations.get("customer:doc:77")).thenReturn(Mono.just(customer));

        cache.findById("c1").test().awaitDone(1, TimeUnit.SECONDS).assertValue(customer);
        cache.findByDocumentNumber("77").test().awaitDone(1, TimeUnit.SECONDS).assertValue(customer);
    }

    @Test
    void putStoresBothKeysWithTheConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), eq(customer), eq(Duration.ofMinutes(10)))).thenReturn(Mono.just(true));

        cache.put(customer).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(valueOperations).set("customer:id:c1", customer, Duration.ofMinutes(10));
        verify(valueOperations).set("customer:doc:77", customer, Duration.ofMinutes(10));
    }

    @Test
    void evictDeletesTheIdKeyAndEveryDocumentKey() {
        when(redisTemplate.delete("customer:id:c1")).thenReturn(Mono.just(1L));
        when(redisTemplate.delete("customer:doc:1", "customer:doc:2")).thenReturn(Mono.just(2L));

        cache.evict("c1", "1", "2", null).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        verify(redisTemplate).delete("customer:doc:1", "customer:doc:2");
    }

    @Test
    void evictWithoutDocumentsOnlyDeletesTheIdKey() {
        when(redisTemplate.delete("customer:id:c1")).thenReturn(Mono.just(1L));

        cache.evict("c1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void redisDownBehavesLikeAnEmptyCacheAndNeverFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Connection refused")));
        when(valueOperations.set(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Connection refused")));
        when(redisTemplate.delete(anyString()))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Connection refused")));

        cache.findById("c1").test().awaitDone(1, TimeUnit.SECONDS).assertNoValues().assertComplete();
        cache.put(customer).test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
        cache.evict("c1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();
    }

    @Test
    void slowRedisIsCutByTheTimeout() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.never());

        cache.findById("c1").test().awaitDone(2, TimeUnit.SECONDS).assertNoValues().assertComplete();
    }
}
