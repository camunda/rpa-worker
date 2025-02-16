package io.camunda.rpa.worker.operate;

import io.camunda.rpa.worker.E2EProperties;
import io.camunda.rpa.worker.zeebe.AuthClient;
import io.camunda.rpa.worker.zeebe.ZeebeAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactivefeign.webclient.WebReactiveFeign;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;

@Configuration
@RequiredArgsConstructor
@Slf4j
class OperateConfiguration {
	
	private final AuthClient authClient;
	private final E2EProperties e2eProperties;
	private final ZeebeAuthProperties zeebeAuthProperties;
	
	@Bean
	public OperateClient operateClient() {
		record TokenWithAbsoluteExpiry(String token, Instant expiry) { }
		Mono<String> authenticator = Mono.defer(() -> authClient.authenticate(new AuthClient.AuthenticationRequest(
								e2eProperties.operateClient(),
								e2eProperties.operateClientSecret(),
								"operate-dedicated",
								"client_credentials"))

						.doOnSubscribe(_ -> log.atInfo()
								.kv("audience","operate.%s".formatted(e2eProperties.camundaHost()))
								.log("Refreshing Operate auth token")))

				.map(resp -> new TokenWithAbsoluteExpiry(
						resp.accessToken(),
						Instant.now().minusSeconds(60).plusSeconds(resp.expiresIn())))

				.cacheInvalidateIf(token -> token.expiry().isBefore(Instant.now()))
				.map(TokenWithAbsoluteExpiry::token);


		return WebReactiveFeign
				.<OperateClient>builder()
				.addRequestInterceptor(reactiveHttpRequest -> authenticator
						.doOnNext(token -> reactiveHttpRequest
								.headers().put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer %s".formatted(token))))

						.thenReturn(reactiveHttpRequest))
				.target(OperateClient.class, "http://%s/operate/v1".formatted(e2eProperties.camundaHost()));
	}
	
}
