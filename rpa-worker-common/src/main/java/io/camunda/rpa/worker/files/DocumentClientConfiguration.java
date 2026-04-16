package io.camunda.rpa.worker.files;

import io.camunda.client.spring.configuration.CamundaClientAllAutoConfiguration;
import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.rpa.worker.net.WebClientProvisioner;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import io.camunda.rpa.worker.zeebe.ZeebeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Import(CamundaClientAllAutoConfiguration.class) // TODO / FIXME
@ConditionalOnCamundaClientEnabled
class DocumentClientConfiguration {

	private final ZeebeProperties zeebeProperties;

	@Bean
	public DocumentClient documentClient(
			WebClientProvisioner webClientProvisioner, 
			CamundaClientProperties camundaClientProperties, 
			ZeebeAuthenticationService zeebeAuthenticationService) {

		Mono<String> authenticator = zeebeAuthenticationService.getAuthToken(
				camundaClientProperties.getAuth().getClientId(),
				camundaClientProperties.getAuth().getClientSecret(),
				camundaClientProperties.getAuth().getAudience());

		DocumentClient client = HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClientProvisioner.webClient(b -> b
						.baseUrl(camundaClientProperties.getRestAddress() + "/v2/")
						.filter(zeebeProperties.authMethod().interceptor(authenticator)))))
				.build()
				.createClient(DocumentClient.class);

		return new RetryingDocumentClientWrapper(client);
	}
}
