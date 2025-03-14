package io.camunda.rpa.worker.secrets.camunda;

import io.camunda.rpa.worker.secrets.SecretsBackend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
class CamundaSecretsBackend implements SecretsBackend {
	
	private final CamundaSecretsClient camundaSecretsClient;

	@Override
	public String getKey() {
		return "camunda";
	}

	@Override
	public Mono<Map<String, Object>> getSecrets() {
		return camundaSecretsClient.getSecrets();
	}
}
