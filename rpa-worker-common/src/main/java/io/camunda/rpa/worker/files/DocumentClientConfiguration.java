package io.camunda.rpa.worker.files;

import io.camunda.rpa.worker.zeebe.ZeebeAuthProperties;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import io.camunda.rpa.worker.zeebe.ZeebeProperties;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientAllAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.webclient.WebReactiveFeign;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Import(ZeebeClientAllAutoConfiguration.class) // TODO: Cx!
@ConditionalOnCamundaClientEnabled
class DocumentClientConfiguration {

	private final ZeebeAuthProperties zeebeAuthProperties;
	private final ZeebeProperties zeebeProperties;
	private final CamundaClientProperties camundaClientProperties;

	@Bean
	@ConditionalOnBean(CamundaClientProperties.class) // TODO: Cx!
	public DocumentClient documentClient(
			WebClient.Builder webClientBuilder, 
			CamundaClientProperties camundaClientProperties, 
			ZeebeAuthenticationService zeebeAuthenticationService) {
		
		Mono<String> authenticator = zeebeAuthenticationService.getAuthToken(
				zeebeAuthProperties.clientId(), 
				zeebeAuthProperties.clientSecret(), 
				camundaClientProperties.getZeebe().getAudience());
		
		DocumentClient client = WebReactiveFeign
				.<DocumentClient>builder(webClientBuilder)
				.addRequestInterceptor(zeebeProperties.authMethod().interceptor(authenticator))
				.target(DocumentClient.class, camundaClientProperties.getZeebe().getBaseUrl() + "/v2/");
		
		return new RetryingDocumentClientWrapper(client);
	}
}
