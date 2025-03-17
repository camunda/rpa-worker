package io.camunda.rpa.worker.secrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor;
import io.camunda.rpa.worker.robot.PreparedScript;
import io.camunda.rpa.worker.workspace.Workspace;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
class SecretsEnvironmentVariablesContributor implements EnvironmentVariablesContributor {
	
	private final SecretsService secretsService;
	private final ObjectMapper objectMapper;
	
	@Override
	public Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script) {
		return secretsService.getSecrets()
				.defaultIfEmpty(Collections.emptyMap())
				.map(secrets -> Try.of(() -> objectMapper.writeValueAsString(secrets)).get())
				.map(secretsJson -> Map.of("CAMUNDA_SECRETS", secretsJson));
	}
}
