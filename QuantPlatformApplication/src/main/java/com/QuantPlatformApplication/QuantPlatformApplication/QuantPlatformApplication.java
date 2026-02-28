package com.QuantPlatformApplication.QuantPlatformApplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuantPlatformApplication {
	public static void main(String[] args) {
		SpringApplication.run(QuantPlatformApplication.class, args);
	}

}
