package io.camunda.rpa.worker.net;

import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor;
import io.camunda.rpa.worker.robot.PreparedScript;
import io.camunda.rpa.worker.workspace.Workspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
class ProxyEnvironmentVariablesContributor implements EnvironmentVariablesContributor {
	
	private final ProxyConfigurationHelper proxyConfigurationHelper;
	
	@Override
	public Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script) {
		return Mono.just(proxyConfigurationHelper.getForwardEnv());
	}
}
