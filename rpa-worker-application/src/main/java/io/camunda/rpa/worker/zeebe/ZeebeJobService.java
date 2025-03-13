package io.camunda.rpa.worker.zeebe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.pexec.ProcessTimeoutException;
import io.camunda.rpa.worker.robot.ExecutionResults;
import io.camunda.rpa.worker.robot.RobotExecutionListener;
import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.script.ScriptRepository;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class ZeebeJobService  {

	public static final String ZEEBE_JOB_WORKSPACE_PROPERTY = "ZEEBE_JOB";

	static final String LINKED_RESOURCES_HEADER_NAME = "linkedResources";
	static final String TIMEOUT_HEADER_NAME = "camunda::timeout";
	static final String MAIN_SCRIPT_LINK_NAME = "RPAScript";
	static final String BEFORE_SCRIPT_LINK_NAME = "Before";
	static final String AFTER_SCRIPT_LINK_NAME = "After";

	private final ZeebeClient zeebeClient;
	private final RobotService robotService;
	private final ScriptRepository scriptRepository;
	private final ObjectMapper objectMapper;
	private final WorkspaceCleanupService workspaceCleanupService;
	private final ZeebeMetricsService zeebeMetricsService;

	public Mono<Void> handleJob(ActivatedJob job) {
		log.atInfo()
				.kv("task", job.getType())
				.kv("job", job.getKey())
				.log("Received Job from Zeebe");
		
		zeebeMetricsService.onZeebeJobReceived(job.getType());

		Flux<RobotScript> before = getScriptKeys(job, BEFORE_SCRIPT_LINK_NAME).concatMap(scriptRepository::getById);
		Mono<RobotScript> main = getScriptKeys(job, MAIN_SCRIPT_LINK_NAME)
				.single()
				.onErrorMap(thrown ->
								thrown instanceof IndexOutOfBoundsException
										|| thrown instanceof NoSuchElementException,
						thrown -> new IllegalStateException(
								"Failed to find exactly 1 LinkedResource providing the main script", thrown))
				.flatMap(scriptRepository::getById);
		Flux<RobotScript> after = getScriptKeys(job, AFTER_SCRIPT_LINK_NAME).concatMap(scriptRepository::getById);

		return Flux.zip(before.collectList(), main, after.collectList())
				.flatMap(scriptSet ->

						robotService.execute(
										scriptSet.getT2(),
										scriptSet.getT1(),
										scriptSet.getT3(),
										getVariables(job),
										Optional.ofNullable(job.getCustomHeaders().get(TIMEOUT_HEADER_NAME))
												.map(Duration::parse)
												.orElse(null),
										executionListenerFor(job),
										Map.of(ZEEBE_JOB_WORKSPACE_PROPERTY, job), 
										null)

								.doOnSuccess(xr -> (switch (xr.result()) {
									case PASS -> zeebeClient
											.newCompleteCommand(job)
											.variables(xr.outputVariables());

									case FAIL -> failJob(job,
											xr.outputVariables(), 
											"There were task failures", 
											xr.fullLogString());

									case ERROR -> failJob(job,
											xr.outputVariables(),
											"There were task errors",
											xr.fullLogString());
								}).send())

								.doOnSuccess(xr -> {
									switch (xr.result()) {
										case PASS -> zeebeMetricsService.onZeebeJobSuccess(job.getType(), xr.duration());
										case FAIL -> zeebeMetricsService.onZeebeJobFail(job.getType(), "ROBOT_TASKFAIL");
										case ERROR -> zeebeMetricsService.onZeebeJobFail(job.getType(), "ROBOT_ERROR");
									}
								})

								.doOnSuccess(xr -> log.atInfo()
										.kv("task", job.getType())
										.kv("job", job.getKey())
										.kv("results", xr.results())
										.log("Job complete")))

				.onErrorResume(ProcessTimeoutException.class,
						thrown -> Mono.<ExecutionResults>empty()
								.doOnSubscribe(_ -> failJob(job,
										Collections.emptyMap(),
										"The execution timed out",
										thrown.getStderr(), thrown.getStdout())
										.send())

								.doOnSubscribe(_ -> log.atWarn()
										.kv("job", job)
										.log("Execution aborted, timeout exceeded"))
						
								.doOnSubscribe(_ -> zeebeMetricsService.onZeebeJobFail(
										job.getType(), "ROBOT_TIMEOUT")))

				.doOnError(thrown -> log.atError()
						.kv("task", job.getType())
						.kv("job", job.getKey())
						.setCause(thrown)
						.log("Error while executing Job"))

				.doOnError(thrown -> failJob(job,
						Collections.emptyMap(),
						thrown.getMessage())
						.send())
				
				.doOnError(_ -> zeebeMetricsService.onZeebeJobError(job.getType()))

				.onErrorComplete()
				.contextWrite(ctx -> ctx.put(ActivatedJob.class, job))
				.then();
	}

	private RobotExecutionListener executionListenerFor(ActivatedJob job) {
		return new RobotExecutionListener() {
			@SuppressWarnings("ReactiveStreamsUnusedPublisher")
			@Override
			public void afterRobotExecution(Workspace workspace) {
				workspaceCleanupService.deleteWorkspace(workspace);
			}

			@Override
			public void beforeScriptExecution(Workspace workspace, Duration timeout) {
				zeebeClient.newUpdateJobCommand(job)
						.updateTimeout(timeout)
						.send();
			}
		};
	}

	private Flux<String> getScriptKeys(ActivatedJob job, String linkName) {
		List<ZeebeLinkedResource> linkedResources = Optional.ofNullable(
						job.getCustomHeaders().get(LINKED_RESOURCES_HEADER_NAME))
				.map(rawHeader -> Try.of(() -> objectMapper.readValue(rawHeader,
						new TypeReference<List<ZeebeLinkedResource>>() {})).get())
				.stream()
				.flatMap(Collection::stream)
				.filter(r -> r.resourceType().equals("RPA"))
				.filter(r -> r.linkName().equals(linkName))
				.toList();

		return Flux.fromStream(linkedResources.stream().map(ZeebeLinkedResource::resourceKey))
				.doOnNext(resourceKey -> log.atInfo()
						.kv("linkName", linkName)
						.kv("resourceKey", resourceKey)
						.log("Identified resource key for script"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getVariables(ActivatedJob job) {
		return Optional.ofNullable(((Map<String, Object>) job.getVariablesAsMap().get("camundaRpaTaskInput")))
				.orElse(job.getVariablesAsMap());
	}
	
	private FailJobCommandStep1.FailJobCommandStep2 failJob(ActivatedJob job, Map<String, Object> variables, String... errorMessage) {
		return zeebeClient
				.newFailCommand(job)
				.retries(job.getRetries() - 1)
				.variables(variables)
				.errorMessage(String.join("\n\n", errorMessage));
	}
}
