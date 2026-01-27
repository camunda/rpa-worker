package io.camunda.rpa.worker.operate;

import io.camunda.rpa.worker.E2EProperties;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import io.camunda.rpa.worker.zeebe.ZeebeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
class OperateConfiguration {

	private final E2EProperties e2eProperties;
	private final ZeebeAuthenticationService zeebeAuthenticationService;
	private final ZeebeProperties zeebeProperties;
	
	@Bean
	public OperateClient operateClient() {
		Mono<String> authenticator = zeebeAuthenticationService.getAuthToken(
				e2eProperties.operateClient(),
				e2eProperties.operateClientSecret(),
				e2eProperties.operateTokenAudience());

		String operateUrl = Optional.ofNullable(e2eProperties.operateUrl())
				.map(Object::toString)
				.orElse("http://%s/operate/v1".formatted(e2eProperties.camundaHost()));
		
		return new RetryingOperateClientWrapper(HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(WebClient.builder()
						.baseUrl(operateUrl)
						.filter(zeebeProperties.authMethod().interceptor(authenticator))
						.build()))
				.build()
				.createClient(OperateClient.class));
	}
}
