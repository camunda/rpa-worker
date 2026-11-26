package io.camunda.rpa.worker.api;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor;
import io.camunda.rpa.worker.robot.PreparedScript;
import io.camunda.rpa.worker.workspace.Workspace;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.camunda.rpa.worker.zeebe.ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY;

@Service
public class CallbackTokenService implements EnvironmentVariablesContributor {

	private record Capability(String token, Workspace workspace) {}

	private final SecureRandom random = new SecureRandom();
	private final Map<String, Capability> capabilities = new ConcurrentHashMap<>();

	public Workspace openExecution(Workspace workspace) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		Capability capability = new Capability(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), workspace);
		if (capabilities.putIfAbsent(workspace.id(), capability) != null)
			throw new IllegalStateException("Workspace already has an active execution");
		return workspace;
	}

	@Override
	public Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script) {
		return Mono.defer(() -> {
			Capability capability = capabilities.get(workspace.id());
			if (capability == null)
				return Mono.error(new IllegalStateException("Workspace has no active execution"));
			return Mono.just(Map.of("RPA_CALLBACK_TOKEN", capability.token()));
		});
	}

	public boolean authorizesWorkspace(String token, String workspaceId) {
		Capability capability = capabilities.get(workspaceId);
		return capability != null && tokensMatch(capability.token(), token);
	}

	public boolean authorizesJob(String token, long jobKey) {
		return capabilities.values().stream().anyMatch(capability -> {
			ActivatedJob job = capability.workspace().getProperty(ZEEBE_JOB_WORKSPACE_PROPERTY);
			// Sandbox scripts use -1 only for stubbed calls; the controller rejects real commands for this key.
			return (job == null ? jobKey == -1 : job.getKey() == jobKey)
					&& tokensMatch(capability.token(), token);
		});
	}

	public void afterRobotExecution(Workspace workspace) {
		capabilities.remove(workspace.id());
	}

	static boolean tokensMatch(String expected, String supplied) {
		return expected != null && !expected.isEmpty() && supplied != null
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
	}
}
