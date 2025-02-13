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
public class RpaWorkerE2ETestsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RpaWorkerE2ETestsApplication.class, args);
	}

	@Bean
	public WebClient webClient(WebClient.Builder builder) {
		return builder
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
				.build();
	}
}
