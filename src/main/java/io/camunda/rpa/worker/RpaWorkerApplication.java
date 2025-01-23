package io.camunda.rpa.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RpaWorkerApplication {
	
	public static final int EXIT_NO_ZEEBE_CONNECTION = 199;
	public static final int EXIT_NO_PYTHON = 198;
	public static final int EXIT_NO_ROBOT = 197;

	public static void main(String[] args) {
		System.setProperty("spring.config.name", "application,rpa-runtime");
		SpringApplication.run(RpaWorkerApplication.class, args);
	}

	@Bean
	public WebClient webClient(WebClient.Builder builder) {
		return builder
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
				.build();
	}
}
