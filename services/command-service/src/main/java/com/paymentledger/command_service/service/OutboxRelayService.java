package com.paymentledger.command_service.service;

import org.springframework.scheduling.annotation.Scheduled;

public interface OutboxRelayService {

    void relayOutboxMessages();
}
