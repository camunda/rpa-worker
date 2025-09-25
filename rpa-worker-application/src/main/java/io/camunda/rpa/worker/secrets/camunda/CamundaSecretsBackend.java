package io.camunda.rpa.worker.secrets.camunda;

import io.camunda.rpa.worker.secrets.SecretsBackend;
import io.camunda.zeebe.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnCamundaClientEnabled
class CamundaSecretsBackend implements SecretsBackend {
	
	private final CamundaSecretsClient camundaSecretsClient;

	@Override
	public String getKey() {
		return "camunda";
	}

	@Override
	public Mono<Map<String, String>> getSecrets() {
		return camundaSecretsClient.getSecrets();
	}
}
