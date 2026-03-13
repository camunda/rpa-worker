package io.camunda.rpa.worker.secrets.camunda;

import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.rpa.worker.zeebe.ZeebeAuthProperties;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@ConditionalOnCamundaClientEnabled
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

		return HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(WebClient.builder()
						.baseUrl(Optional.ofNullable(clientProperties.secretsEndpoint())
								.map(Object::toString)
								.orElse("http://no-secrets/"))
						.filter((request, next) -> authenticator
								.map(token -> ClientRequest.from(request)
										.header(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token))
										.build())
								.flatMap(next::exchange))
						.build()))
				.build()
				.createClient(CamundaSecretsClient.class);
	}
}
