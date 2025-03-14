package io.camunda.rpa.worker.secrets.camunda;

import feign.RequestLine;
import reactor.core.publisher.Mono;

import java.util.Map;

interface CamundaSecretsClient {
	@RequestLine("GET /secrets")
	Mono<Map<String, Object>> getSecrets();
}
