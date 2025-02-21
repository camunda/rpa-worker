package io.camunda.rpa.worker.operate;

import io.camunda.rpa.worker.E2EProperties;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactivefeign.webclient.WebReactiveFeign;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
class OperateConfiguration {

	private final E2EProperties e2eProperties;
	private final ZeebeAuthenticationService zeebeAuthenticationService;
	
	@Bean
	public OperateClient operateClient() {
		Mono<String> authenticator = zeebeAuthenticationService.getAuthToken(
				e2eProperties.operateClient(),
				e2eProperties.operateClientSecret(),
				e2eProperties.operateTokenAudience());

		String operateUrl = Optional.ofNullable(e2eProperties.operateUrl())
				.map(Object::toString)
				.orElse("http://%s/operate/v1".formatted(e2eProperties.camundaHost()));
				
		return WebReactiveFeign
				.<OperateClient>builder()
				.addRequestInterceptor(reactiveHttpRequest -> authenticator
						.doOnNext(token -> reactiveHttpRequest
								.headers().put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer %s".formatted(token))))

						.thenReturn(reactiveHttpRequest))
				.target(OperateClient.class, operateUrl);
	}
}
