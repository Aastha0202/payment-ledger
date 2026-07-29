package com.paymentledger.command_service.service.impl;

import com.paymentledger.command_service.service.IdempotencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    @Autowired
    StringRedisTemplate redisTemplate;


    @Override
    public boolean isDuplicate(String idempotencyKey) {
        // Implement logic to check if the idempotency key already exists in the database
        return redisTemplate.opsForValue().get(idempotencyKey) != null;
    }

    @Override
    public void store(String idempotencyKey, String response) {
        // Implement logic to store the idempotency key and response in the database
        redisTemplate.opsForValue().set(idempotencyKey, response, 24, TimeUnit.HOURS);
    }

    @Override
    public void storeTemporary(String idempotencyKey, String response) {
        // Implement logic to store the idempotency key and response in the database
        redisTemplate.opsForValue().set(idempotencyKey, response, 60, TimeUnit.SECONDS);
    }

    @Override
    public String getCachedResponse(String idempotencyKey) {
        return redisTemplate.opsForValue().get(idempotencyKey);
    }
}
