package com.paymentledger.projection_service.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqConsumer {

    @Value("${app.kafka.topic.transfer-events-dlq}")
    private String dlqTopic;

    @KafkaListener(
            topics = "${app.kafka.topic.transfer-events-dlq}",
            groupId = "${spring.kafka.dlq.group-id}"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        log.error("DLQ message received — manual review required. " +
                        "Topic: {}, Partition: {}, Offset: {}, " +
                        "Key: {}, Payload: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value());

        // In production: send alert to PagerDuty/Slack
        // For now: log and acknowledge to prevent infinite loop
        acknowledgment.acknowledge();
    }
}
