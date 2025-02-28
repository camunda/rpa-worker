package io.camunda.rpa.worker.secrets;

import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor;
import io.camunda.rpa.worker.robot.PreparedScript;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class SecretsEnvironmentVariablesContributor implements EnvironmentVariablesContributor {
	
	private final SecretsService secretsService;
	
	@Override
	public Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script) {
		return Mono.deferContextual(ctx -> Mono.justOrEmpty(
								ctx.<ActivatedJob>getOrEmpty(ActivatedJob.class))

						.flatMap(_ -> secretsService.getSecrets()
								.map(m -> m.entrySet().stream()
										.map(kv -> Map.entry(
												"SECRET_%s".formatted(kv.getKey().toUpperCase()),
												kv.getValue()))
										.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))))
				
				.defaultIfEmpty(Collections.emptyMap());
	}
}
