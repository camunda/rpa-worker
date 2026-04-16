package io.camunda.rpa.worker;

import io.camunda.rpa.worker.net.WebClientProvisioner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@SpringBootApplication
@ConfigurationPropertiesScan
@Slf4j
public class RpaWorkerE2ETestsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RpaWorkerE2ETestsApplication.class, args);
	}

	@Bean
	public WebClient webClient(WebClientProvisioner webClientProvisioner) {
		return webClientProvisioner.webClient();
	}

	@Bean
	public RpaWorkerClient rpaWorkerClient(WebClientProvisioner webClientProvisioner) {
		return HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClientProvisioner.webClient(b -> b
						.baseUrl("http://localhost:36227/"))))
				.build()
				.createClient(RpaWorkerClient.class);
	}
}
