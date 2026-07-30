package com.paymentledger.command_service;

import com.paymentledger.command_service.config.TestKafkaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestKafkaConfig.class)
class CommandServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
