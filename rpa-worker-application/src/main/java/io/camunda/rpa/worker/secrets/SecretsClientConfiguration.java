package io.camunda.rpa.worker.secrets;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.webclient.WebReactiveFeign;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
class SecretsClientConfiguration {

	private final SecretsClientProperties clientProperties;
	
	@Bean
	public SecretsClient secretsClient(WebClient.Builder webClientBuilder) {
		return WebReactiveFeign
				.<SecretsClient>builder(webClientBuilder)
				.target(SecretsClient.class, Optional.ofNullable(clientProperties.secretsEndpoint())
						.map(Object::toString)
						.orElse("http://no-secrets/"));
	}
	
}
