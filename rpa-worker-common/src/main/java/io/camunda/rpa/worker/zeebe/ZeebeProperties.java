package io.camunda.rpa.worker.zeebe;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.Set;

@ConfigurationProperties("camunda.rpa.zeebe")
@Builder(toBuilder = true)
public record ZeebeProperties(
		String rpaTaskPrefix,
		Set<String> workerTags,
		int maxConcurrentJobs, 
		AuthMethod authMethod) { 
	
	@RequiredArgsConstructor
	public enum AuthMethod {
		TOKEN((auth, req, chain) -> auth
				.flatMap(token -> sendWithHeader(
						req, chain, HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(token)))),
		
		COOKIE((auth, req, chain) -> auth
				.flatMap(token -> sendWithHeader(
						req, chain, HttpHeaders.COOKIE, "OPERATE-SESSION=%s".formatted(token)))),

		BASIC((auth, req, chain) -> auth
				.flatMap(h -> sendWithHeader(req, chain, HttpHeaders.AUTHORIZATION, h))),
		
		NONE((_, req, chain) -> chain.exchange(req));
		
		private final TriFunction<Mono<String>, ClientRequest, ExchangeFunction, Mono<ClientResponse>> interceptor;

		public ExchangeFilterFunction interceptor(Mono<String> authenticator) {
			return (request, next) -> 
					interceptor.apply(authenticator, request, next);
		}
		
		private static Mono<ClientResponse> sendWithHeader(ClientRequest request, ExchangeFunction chain, String headerName, String headerValue) {
			ClientRequest modifiedRequest = ClientRequest.from(request)
					.header(headerName, headerValue)
					.build();
			return chain.exchange(modifiedRequest);
		}
	}
}
