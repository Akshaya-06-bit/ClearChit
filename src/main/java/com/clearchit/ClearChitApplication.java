package com.clearchit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling

public class ClearChitApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClearChitApplication.class, args);
	}

}
