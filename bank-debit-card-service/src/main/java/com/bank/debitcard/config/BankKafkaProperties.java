package com.bank.debitcard.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topics y politica de reintentos de Kafka, servidos por el Config Server bajo
 * el prefijo {@code bank.kafka}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.kafka")
public class BankKafkaProperties {

    /** Particiones de los topics que crea este servicio. */
    private int partitions = 3;

    /** Replicas de los topics que crea este servicio (1 en desarrollo). */
    private short replicas = 1;

    /** Nombre logico del topic a nombre real, por ejemplo {@code customer-events: bank.customer.events}. */
    private Map<String, String> topics = new HashMap<>();

    /** Topics (nombres logicos) que publica este servicio: aqui se crean. */
    private List<String> published = new ArrayList<>();

    /** Topics (nombres logicos) que consume este servicio: aqui se crea su {@code .DLT}. */
    private List<String> consumed = new ArrayList<>();

    /** Reintentos antes de enviar un mensaje al DLT. */
    private Retry retry = new Retry();

    /**
     * Devuelve el nombre real de un topic.
     *
     * @param logicalName nombre logico, por ejemplo {@code customer-events}
     * @return nombre del topic en Kafka
     * @throws IllegalStateException si el topic no esta configurado
     */
    public String topic(String logicalName) {
        String name = topics.get(logicalName);
        if (name == null) {
            throw new IllegalStateException("Kafka topic '" + logicalName + "' is not configured");
        }
        return name;
    }

    /**
     * Backoff exponencial de los reintentos de consumo.
     */
    @Getter
    @Setter
    public static class Retry {

        /** Espera antes del primer reintento. */
        private Duration initialInterval = Duration.ofSeconds(1);

        /** Factor de crecimiento de la espera. */
        private double multiplier = 2.0;

        /** Reintentos antes de mandar el mensaje al DLT. */
        private int maxRetries = 3;
    }
}
