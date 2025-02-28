package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor;
import io.camunda.rpa.worker.robot.PreparedScript;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Component
class ZeebeEnvironmentVariablesContributor implements EnvironmentVariablesContributor {
	@Override
	public Mono<Map<String, String>> getEnvironmentVariables(Workspace workspace, PreparedScript script) {
		return Mono.deferContextual(ctx -> Mono.justOrEmpty(
						ctx.<ActivatedJob>getOrEmpty(ActivatedJob.class)

								.map(job -> Map.of(
										"RPA_ZEEBE_JOB_KEY", String.valueOf(job.getKey()),
										"RPA_ZEEBE_JOB_TYPE", job.getType(),
										"RPA_ZEEBE_BPMN_PROCESS_ID", job.getBpmnProcessId(),
										"RPA_ZEEBE_PROCESS_INSTANCE_KEY", String.valueOf(job.getProcessInstanceKey())))))

				.defaultIfEmpty(Collections.emptyMap());
	}
}
