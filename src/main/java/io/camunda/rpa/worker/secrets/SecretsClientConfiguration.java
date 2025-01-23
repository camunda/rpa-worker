package io.camunda.rpa.worker.secrets;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.webclient.WebReactiveFeign;

@Configuration
@RequiredArgsConstructor
class SecretsClientConfiguration {

	private final SecretsClientProperties clientProperties;
	
	@Bean
	public AuthClient authClient(WebClient.Builder webClientBuilder) {
		return WebReactiveFeign
				.<AuthClient>builder(webClientBuilder)
				.target(AuthClient.class, clientProperties.authEndpoint().toString());
	}
	
	@Bean
	public SecretsClient secretsClient(WebClient.Builder webClientBuilder) {
		return WebReactiveFeign
				.<SecretsClient>builder(webClientBuilder)
				.target(SecretsClient.class, clientProperties.secretsEndpoint().toString());
	}
}
