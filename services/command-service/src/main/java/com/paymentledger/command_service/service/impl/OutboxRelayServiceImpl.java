package com.paymentledger.command_service.service.impl;

import com.paymentledger.command_service.constants.OutboxStatus;
import com.paymentledger.command_service.entity.Outbox;
import com.paymentledger.command_service.repository.OutboxRepository;
import com.paymentledger.command_service.service.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayServiceImpl implements OutboxRelayService {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topic.transfer-events}")
    private String transferEventsTopic;

    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 100;

    @Override
    @Scheduled(fixedDelay = 2000)
    public void relayOutboxMessages() {
        Page<Outbox> pending = outboxRepository
                .findByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) {
            return;
        }

        log.info("Outbox relay: found {} pending messages",
                pending.getNumberOfElements());

        for (Outbox message : pending) {
            try {
                // Send and WAIT for confirmation
                kafkaTemplate.send(
                        transferEventsTopic,
                        message.getAggregateId().toString(),
                        message.getPayload()
                ).get();

                message.setStatus(OutboxStatus.PROCESSED);
                message.setProcessedAt(LocalDateTime.now());
                log.info("Published outbox message: {} type: {}",
                        message.getId(), message.getEventType());

            } catch (Exception e) {
                log.error("Failed to publish outbox message: {}",
                        message.getId(), e);
                message.setRetryCount(message.getRetryCount() + 1);
                if (message.getRetryCount() >= MAX_RETRIES) {
                    message.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox message {} exceeded max retries,"
                            + " marking FAILED", message.getId());
                }
            } finally {
                outboxRepository.save(message);
            }
        }
    }
}
