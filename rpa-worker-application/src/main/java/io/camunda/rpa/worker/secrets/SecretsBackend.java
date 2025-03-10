package io.camunda.rpa.worker.secrets;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface SecretsBackend {
	String getKey();
	Mono<Map<String, String>> getSecrets();
}
