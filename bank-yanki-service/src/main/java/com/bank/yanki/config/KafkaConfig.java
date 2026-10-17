package com.bank.yanki.config;

import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Topics y manejo de errores de Kafka.
 *
 * <ul>
 *   <li>Crea los topics que publica este servicio y tambien los que consume,
 *       con su {@code .DLT}. Declarar los consumidos es idempotente y evita
 *       depender del orden de arranque: si el consumidor se suscribe antes de
 *       que exista el topic, el broker lo crearia con otra cantidad de
 *       particiones y el consumidor no veria las nuevas hasta refrescar
 *       metadatos.</li>
 *   <li>Reintenta con backoff exponencial y, agotados los reintentos, envia el
 *       mensaje a {@code <topic>.DLT} y lo registra con nivel ERROR.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    /** Sufijo de los topics de mensajes fallidos. */
    static final String DLT_SUFFIX = ".DLT";

    private final BankKafkaProperties properties;

    /**
     * Topics que este servicio crea al arrancar (si ya existen, no se tocan).
     *
     * @return topics publicados y DLT de los consumidos
     */
    @Bean
    public KafkaAdmin.NewTopics bankTopics() {
        return new KafkaAdmin.NewTopics(topicDefinitions().toArray(NewTopic[]::new));
    }

    /**
     * Arma la lista de topics a crear, sin repetidos.
     *
     * @return topics publicados, consumidos y DLT de los consumidos
     */
    List<NewTopic> topicDefinitions() {
        Stream<String> published = properties.getPublished().stream().map(properties::topic);
        Stream<String> consumed = properties.getConsumed().stream().map(properties::topic);
        Stream<String> deadLetters = properties.getConsumed().stream()
                .map(properties::topic)
                .map(topic -> topic + DLT_SUFFIX);
        return Stream.of(published, consumed, deadLetters).flatMap(stream -> stream)
                .distinct()
                .map(this::newTopic)
                .toList();
    }

    /**
     * Manejador de errores de los listeners: reintentos con backoff y luego DLT.
     *
     * <p>Spring Boot lo aplica automaticamente al container factory. Los errores
     * de deserializacion no se reintentan: van directo al DLT.</p>
     *
     * @param kafkaTemplate plantilla usada para publicar en el DLT
     * @return manejador de errores
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate);
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(properties.getRetry().getMaxRetries());
        backOff.setInitialInterval(properties.getRetry().getInitialInterval().toMillis());
        backOff.setMultiplier(properties.getRetry().getMultiplier());
        return new DefaultErrorHandler((consumerRecord, exception) -> {
            log.error("Message on {} key={} failed after retries, sending it to {}{}: {}",
                    consumerRecord.topic(), consumerRecord.key(), consumerRecord.topic(), DLT_SUFFIX,
                    exception.getMessage(), exception);
            deadLetter.accept(consumerRecord, exception);
        }, backOff);
    }

    /**
     * Define un topic con las particiones y replicas configuradas.
     *
     * @param name nombre del topic
     * @return definicion del topic
     */
    private NewTopic newTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicas())
                .build();
    }
}
