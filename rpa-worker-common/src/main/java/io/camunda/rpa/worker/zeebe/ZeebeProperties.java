package io.camunda.rpa.worker.zeebe;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

@ConfigurationProperties("camunda.rpa.zeebe")
@Builder(toBuilder = true)
public record ZeebeProperties(
		String rpaTaskPrefix,
		Set<String> workerTags,
		URI authEndpoint, 
		int maxConcurrentJobs, 
		AuthMethod authMethod) { 
	
	@RequiredArgsConstructor
	public enum AuthMethod {
		TOKEN((auth, req) -> auth
						.doOnNext(token -> req
								.headers().put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer %s".formatted(token))))
						.thenReturn(req)),
		
		COOKIE((auth, req) -> auth
						.doOnNext(token -> req
								.headers().put(HttpHeaders.COOKIE, Collections.singletonList("OPERATE-SESSION=%s".formatted(token))))
						.thenReturn(req));
		
		private final BiFunction<Mono<String>, ReactiveHttpRequest, Mono<ReactiveHttpRequest>> interceptor;

		public ReactiveHttpRequestInterceptor interceptor(Mono<String> authenticator) {
			return request -> interceptor.apply(authenticator, request);
		}
	}
}
