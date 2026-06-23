package com.viettelDigitalTalent.EntitiyManagement.queue.config;

public final class KafkaTopicConstants {

    public static final String RAW_LOGS          = "raw-logs";
    public static final String NORMALIZED_EVENTS = "normalized-events";
    public static final String ENRICHED_EVENTS   = "enriched-events";
    public static final String DEAD_LETTER_QUEUE = "dead-letter-queue";

    private KafkaTopicConstants() {}
}
