package io.camunda.rpa.worker.zeebe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.pexec.ProcessTimeoutException;
import io.camunda.rpa.worker.robot.ExecutionResults;
import io.camunda.rpa.worker.robot.RobotExecutionListener;
import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.script.ScriptRepository;
import io.camunda.rpa.worker.secrets.SecretsService;
import io.camunda.rpa.worker.util.LoopingListIterator;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ZeebeJobService implements ApplicationListener<ZeebeReadyEvent> {

	public static final String ZEEBE_JOB_WORKSPACE_PROPERTY = "ZEEBE_JOB";

	static final String LINKED_RESOURCES_HEADER_NAME = "linkedResources";
	static final String TIMEOUT_HEADER_NAME = "camunda::timeout";
	static final String MAIN_SCRIPT_LINK_NAME = "RPAScript";
	static final String BEFORE_SCRIPT_LINK_NAME = "Before";
	static final String AFTER_SCRIPT_LINK_NAME = "After";
	static final Duration JOB_POLL_TIME = Duration.ofMillis(200);

	private final ZeebeClient zeebeClient;
	private final ZeebeProperties zeebeProperties;
	private final RobotService robotService;
	private final ScriptRepository scriptRepository;
	private final ObjectMapper objectMapper;
	private final SecretsService secretsService;
	private final WorkspaceCleanupService workspaceCleanupService;

	@Override
	public void onApplicationEvent(ZeebeReadyEvent ignored) {
		doInit();
	}

	ZeebeJobService doInit() {

		log.atInfo()
				.kv("workerTags", zeebeProperties.workerTags())
				.log("Accepting Zeebe jobs for tags");

		LoopingListIterator<String> tagIterator = new LoopingListIterator<>(zeebeProperties.workerTags().stream()
				.map(t -> zeebeProperties.rpaTaskPrefix() + t)
				.collect(Collectors.toList()));

		Flux.fromIterable(() -> tagIterator)
				.flatMap(jobType -> Mono.fromCompletionStage(zeebeClient.newActivateJobsCommand()
								.jobType(jobType)
								.maxJobsToActivate(1)
								.requestTimeout(JOB_POLL_TIME)
								.send())

						.onErrorReturn(thrown -> thrown instanceof StatusRuntimeException srex
								&& srex.getStatus().getCode() == Status.Code.UNAVAILABLE
								&& srex.getStatus().getDescription().contains("shutdown"), Collections::emptyList)
						
						.doOnError(thrown -> log.atError()
								.setCause(thrown)
								.log("Error polling Zeebe for jobs"))
						.onErrorReturn(Collections::emptyList)
						
						.doOnSubscribe(_ -> log.atDebug()
								.kv("jobType", jobType)
								.log("Polling for job"))
						
						.flatMapIterable(ActivateJobsResponse::getJobs)
						.flatMap(this::handleJob), zeebeProperties.maxConcurrentJobs())
				.subscribe();

		return this;
	}

	private Mono<Void> handleJob(ActivatedJob job) {
		log.atInfo()
				.kv("task", job.getType())
				.kv("job", job.getKey())
				.log("Received Job from Zeebe");

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
				.flatMap(scriptSet -> getSecretsAsEnv(job)
						.flatMap(secrets ->

								robotService.execute(
										scriptSet.getT2(),
										scriptSet.getT1(),
										scriptSet.getT3(),
										getVariables(job),
										secrets,
										Optional.ofNullable(job.getCustomHeaders().get(TIMEOUT_HEADER_NAME))
												.map(Duration::parse)
												.orElse(null),
										executionListenerFor(job),
										getZeebeEnvironment(job),
										Map.of(ZEEBE_JOB_WORKSPACE_PROPERTY, job)))

						.doOnSuccess(xr -> (switch (xr.result()) {
							case PASS -> zeebeClient
									.newCompleteCommand(job)
									.variables(xr.outputVariables());

							case FAIL -> zeebeClient
									.newThrowErrorCommand(job)
									.errorCode("ROBOT_TASKFAIL")
									.errorMessage("There were task failures");

							case ERROR -> zeebeClient
									.newThrowErrorCommand(job)
									.errorCode("ROBOT_ERROR")
									.errorMessage("There were task errors");
						}).send())


						.doOnSuccess(xr -> log.atInfo()
								.kv("task", job.getType())
								.kv("job", job.getKey())
								.kv("results", xr.results())
								.log("Job complete")))

				.onErrorResume(ProcessTimeoutException.class,
						_ -> Mono.<ExecutionResults>empty()
								.doOnSubscribe(_ -> zeebeClient
										.newThrowErrorCommand(job)
										.errorCode("ROBOT_TIMEOUT")
										.errorMessage("The execution timed out")
										.send())

								.doOnSubscribe(_ -> log.atWarn()
										.kv("job", job)
										.log("Execution aborted, timeout exceeded")))

				.doOnError(thrown -> log.atError()
						.kv("task", job.getType())
						.kv("job", job.getKey())
						.setCause(thrown)
						.log("Error while executing Job"))

				.doOnError(thrown -> zeebeClient
						.newFailCommand(job)
						.retries(job.getRetries() - 1)
						.errorMessage(thrown.getMessage())
						.send())

				.onErrorComplete()
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

	private Mono<Map<String, String>> getSecretsAsEnv(ActivatedJob job) {
		return secretsService.getSecrets()
				.map(m -> m.entrySet().stream()
						.map(kv -> Map.entry(
								"SECRET_%s".formatted(kv.getKey().toUpperCase()),
								kv.getValue()))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
	}

	private Map<String, String> getZeebeEnvironment(ActivatedJob job) {
		return Map.of(
				"RPA_ZEEBE_JOB_KEY", String.valueOf(job.getKey()),
				"RPA_ZEEBE_JOB_TYPE", job.getType(),
				"RPA_ZEEBE_BPMN_PROCESS_ID", job.getBpmnProcessId(),
				"RPA_ZEEBE_PROCESS_INSTANCE_KEY", String.valueOf(job.getProcessInstanceKey()));
	}

}
