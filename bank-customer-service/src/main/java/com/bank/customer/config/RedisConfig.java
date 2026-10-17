package com.bank.customer.config;

import com.bank.customer.model.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Plantilla reactiva de Redis para la cache de clientes: claves de texto y
 * valores en JSON.
 */
@Configuration
public class RedisConfig {

    /**
     * Plantilla que guarda clientes como JSON.
     *
     * <p>Usa el {@link ObjectMapper} de Spring Boot, que ya sabe serializar las
     * fechas de Java 8.</p>
     *
     * @param connectionFactory conexion reactiva (Lettuce) configurada por Spring Boot
     * @param objectMapper      mapper JSON de la aplicacion
     * @return plantilla de clientes
     */
    @Bean
    public ReactiveRedisTemplate<String, Customer> customerRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<Customer> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Customer.class);
        RedisSerializationContext<String, Customer> context = RedisSerializationContext
                .<String, Customer>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
