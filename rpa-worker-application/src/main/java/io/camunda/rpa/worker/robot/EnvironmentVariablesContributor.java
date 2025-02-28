package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.workspace.Workspace;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface EnvironmentVariablesContributor {
	Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script);
}
