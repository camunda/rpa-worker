package io.camunda.rpa.worker.secrets.camunda;

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
class CamundaSecretsClientConfiguration {

	private final CamundaSecretsClientProperties clientProperties;
	private final ObjectProvider<ZeebeAuthenticationService> zeebeAuthenticationService;
	private final ZeebeAuthProperties zeebeAuthProperties;
	
	@Bean
	public CamundaSecretsClient secretsClient(WebClient.Builder webClientBuilder, CamundaSecretsClientProperties camundaSecretsClientProperties) {
		Mono<String> authenticator = zeebeAuthenticationService.getObject().getAuthToken(
				zeebeAuthProperties.clientId(),
				zeebeAuthProperties.clientSecret(),
				camundaSecretsClientProperties.tokenAudience());
		
		return WebReactiveFeign
				.<CamundaSecretsClient>builder(webClientBuilder)
				.addRequestInterceptor(reactiveHttpRequest -> authenticator
						.doOnNext(token -> reactiveHttpRequest
								.headers().put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer %s".formatted(token))))
						.thenReturn(reactiveHttpRequest))
				.target(CamundaSecretsClient.class, Optional.ofNullable(clientProperties.secretsEndpoint())
						.map(Object::toString)
						.orElse("http://no-secrets/"));
	}
	
}
