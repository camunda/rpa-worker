package io.camunda.rpa.worker.secrets;

import io.camunda.rpa.worker.zeebe.ZeebeAuthProperties;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.webclient.WebReactiveFeign;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Optional;

@Configuration
@RequiredArgsConstructor
class SecretsClientConfiguration {

	private final SecretsClientProperties clientProperties;
	private final ObjectProvider<ZeebeAuthenticationService> zeebeAuthenticationService;
	private final ZeebeAuthProperties zeebeAuthProperties;
	
	@Bean
	public SecretsClient secretsClient(WebClient.Builder webClientBuilder, SecretsClientProperties secretsClientProperties) {
		Mono<String> authenticator = zeebeAuthenticationService.getObject().getAuthToken(
				zeebeAuthProperties.clientId(),
				zeebeAuthProperties.clientSecret(),
				secretsClientProperties.tokenAudience());
		
		return WebReactiveFeign
				.<SecretsClient>builder(webClientBuilder)
				.addRequestInterceptor(reactiveHttpRequest -> authenticator
						.doOnNext(token -> reactiveHttpRequest
								.headers().put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer %s".formatted(token))))
						.thenReturn(reactiveHttpRequest))
				.target(SecretsClient.class, Optional.ofNullable(clientProperties.secretsEndpoint())
						.map(Object::toString)
						.orElse("http://no-secrets/"));
	}
	
}
