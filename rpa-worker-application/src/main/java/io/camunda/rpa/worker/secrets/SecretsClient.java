package io.camunda.rpa.worker.secrets;

import feign.RequestLine;
import reactor.core.publisher.Mono;

import java.util.Map;

interface SecretsClient {
	@RequestLine("GET /secrets")
	Mono<Map<String, String>> getSecrets();
}
