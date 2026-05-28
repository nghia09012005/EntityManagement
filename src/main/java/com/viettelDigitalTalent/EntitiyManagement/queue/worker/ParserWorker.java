package com.viettelDigitalTalent.EntitiyManagement.queue.worker;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ParserWorker {

    @Autowired
    private ParserDispatcher parserDispatcher;

    @Autowired private TaskExecutor taskExecutor;

    @Autowired private EnrichmentService enrichService;

    // Lắng nghe topic 'raw-logs'
    @KafkaListener(topics = "raw-logs", groupId = "soc-parser-group")
    public void consume(ConsumerRecord<String, String> record) {
        String source = record.key(); // Source là key của Kafka message
        String rawData = record.value();

        try {
            // Chuyển raw log thành BaseEvent
            BaseEvent event = parserDispatcher.parse(source, rawData);

            // Ở đây bạn có thể đẩy tiếp vào topic 'normalized-events'
            // để các module Enrichment/Detection xử lý tiếp
            log.info("Parsed event: " + event.getClass().getSimpleName());

            // 1. Chạy Enrichment trên một Thread
            CompletableFuture.runAsync(() -> enrichEvent(event), taskExecutor);

            // 2. Chạy Save DB trên một Thread khác
            CompletableFuture.runAsync(() -> saveToDatabase(event), taskExecutor);


        } catch (Exception e) {

            log.error("Lỗi parse log từ " + source + ": " + e.getMessage());
        }
    }


    // lưu enrich và data vào neo4j
    private void enrichEvent(BaseEvent event) {
        String threadName = Thread.currentThread().getName();
        log.info("[" + threadName + "] Đang enrich dữ liệu cho: " + event.getCategory());


        enrichService.enrich(event);

        // Giả lập xử lý
        // try { Thread.sleep(500); } catch (InterruptedException e) {}
        log.info("[" + threadName + "] Enrich xong!");
    }

    // Lưu audit log
    private void saveToDatabase(BaseEvent event) {
        String threadName = Thread.currentThread().getName();
        log.info("[" + threadName + "] Đang lưu vào MongoDB...");
        // Giả lập lưu DB
        try { Thread.sleep(300); } catch (InterruptedException e) {}
        log.info("[" + threadName + "] Đã lưu vào MongoDB!");
    }


}