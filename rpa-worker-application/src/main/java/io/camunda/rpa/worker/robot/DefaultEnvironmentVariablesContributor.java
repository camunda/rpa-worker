package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.workspace.Workspace;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
class DefaultEnvironmentVariablesContributor implements EnvironmentVariablesContributor {
	
	@Override
	public Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script) {
		
		return Mono.just(Map.of(
				"RPA_WORKSPACE", workspace.path().toAbsolutePath().toString(),
				"RPA_WORKSPACE_ID", workspace.path().getFileName().toString(),
				"ROBOT_ARTIFACTS", workspace.path().resolve("robot_artifacts").toAbsolutePath().toString(),

				"RPA_SCRIPT", script.script().id(),
				"RPA_EXECUTION_KEY", script.executionKey()));
	}
}
