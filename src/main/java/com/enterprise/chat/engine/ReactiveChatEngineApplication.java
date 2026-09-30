package com.enterprise.chat.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReactiveChatEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReactiveChatEngineApplication.class, args);
	}

}
