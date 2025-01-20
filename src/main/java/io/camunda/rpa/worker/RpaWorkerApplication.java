package io.camunda.rpa.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RpaWorkerApplication {
	
	public static final int EXIT_NO_ZEEBE_CONNECTION = 199;

	public static void main(String[] args) {
		System.setProperty("spring.config.name", "application,rpa-runtime");
		SpringApplication.run(RpaWorkerApplication.class, args);
	}
}
