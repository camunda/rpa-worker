package io.camunda.rpa.worker.secrets.camunda;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@HttpExchange
interface CamundaSecretsClient {
	@GetExchange("/secrets")
	Mono<Map<String, Object>> getSecrets();
}
