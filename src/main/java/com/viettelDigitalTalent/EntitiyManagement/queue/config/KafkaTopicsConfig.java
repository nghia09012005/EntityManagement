package com.viettelDigitalTalent.EntitiyManagement.queue.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.viettelDigitalTalent.EntitiyManagement.queue.config.KafkaTopicConstants.*;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic normalizedEventsTopic() {
        return TopicBuilder.name(NORMALIZED_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic enrichedEventsTopic() {
        return TopicBuilder.name(ENRICHED_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic deadLetterQueueTopic() {
        return TopicBuilder.name(DEAD_LETTER_QUEUE).partitions(1).replicas(1).build();
    }
}
