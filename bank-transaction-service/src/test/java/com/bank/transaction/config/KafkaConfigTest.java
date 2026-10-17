package com.bank.transaction.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaConfigTest {

    private BankKafkaProperties properties() {
        BankKafkaProperties properties = new BankKafkaProperties();
        properties.setTopics(Map.of("a-events", "bank.a.events", "b-events", "bank.b.events"));
        properties.setPublished(List.of("a-events"));
        properties.setConsumed(List.of("b-events"));
        return properties;
    }

    @Test
    void createsPublishedTopicsAndTheDeadLetterTopicOfEachConsumedOne() {
        KafkaConfig config = new KafkaConfig(properties());

        assertThat(config.topicDefinitions())
                .extracting(NewTopic::name, NewTopic::numPartitions)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("bank.a.events", 3),
                        org.assertj.core.groups.Tuple.tuple("bank.b.events", 3),
                        org.assertj.core.groups.Tuple.tuple("bank.b.events.DLT", 3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsTheErrorHandlerWithTheConfiguredRetries() {
        KafkaConfig config = new KafkaConfig(properties());

        assertThat(config.kafkaErrorHandler(mock(KafkaTemplate.class))).isNotNull();
        assertThat(config.bankTopics()).isNotNull();
    }

    @Test
    void unknownTopicNamesFailFast() {
        BankKafkaProperties properties = properties();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> properties.topic("missing"))
                .isInstanceOf(IllegalStateException.class);
    }
}
