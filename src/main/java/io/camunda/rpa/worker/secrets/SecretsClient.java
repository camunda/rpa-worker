package io.camunda.rpa.worker.secrets;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import reactor.core.publisher.Mono;

import java.util.Map;

interface SecretsClient {
	@RequestLine("GET /secrets")
	@Headers("Authorization: Bearer {authToken}")
	Mono<Map<String, String>> getSecrets(@Param String authToken);
}
