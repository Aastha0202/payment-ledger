package com.paymentledger.projection_service.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentledger.projection_service.entity.ConsumedEvent;
import com.paymentledger.projection_service.repository.ConsumedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferEventConsumer {

    private final ConsumedEventRepository consumedEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(
            topics = "${app.kafka.topic.transfer-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        String eventIdString = record.topic() + "-"
                + record.partition() + "-"
                + record.offset();

        // Generate deterministic UUID from event identifier
        UUID eventId = UUID.nameUUIDFromBytes(eventIdString.getBytes());

        try {
            processEvent(record, eventId);
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged " +
                    "event: {}", eventId);
        } catch (Exception e) {
            log.error("Failed to process event: {}. " +
                    "Will be redelivered.", eventId, e);
            // No acknowledge — Kafka redelivers the message
        }
    }

    @Transactional
    protected void processEvent(
            ConsumerRecord<String, String> record,
            UUID eventId) throws Exception {

        // Deduplication check
        if (consumedEventRepository.existsByEventId(eventId)) {
            log.info("Event {} already processed. Skipping.",
                    eventId);
            return;
        }

        // Parse payload
        Map<String, Object> payload = objectMapper.readValue(
                record.value(),
                new TypeReference<>() {
                });

        // Mark as consumed — same transaction
        consumedEventRepository.save(ConsumedEvent.builder()
                .eventId(eventId)
                .eventType(record.topic())
                .createdAt(LocalDateTime.now())
                .build());

        log.info("Processed transfer event: {}",      eventId);
    }
}