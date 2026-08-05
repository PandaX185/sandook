package com.sandook.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SandookBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SandookBackendApplication.class, args);
	}

}
