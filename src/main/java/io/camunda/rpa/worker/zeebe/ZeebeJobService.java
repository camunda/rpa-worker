package io.camunda.rpa.worker.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.pexec.ProcessTimeoutException;
import io.camunda.rpa.worker.robot.ExecutionResults;
import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.robot.WorkspaceService;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.script.ScriptRepository;
import io.camunda.rpa.worker.secrets.SecretsService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.vavr.control.Try;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ZeebeJobService implements ApplicationListener<ZeebeReadyEvent> {
	
	static final String LINKED_RESOURCES_HEADER_NAME = "camunda::linkedResources";
	static final String TIMEOUT_HEADER_NAME = "camunda::timeout";
	static final String MAIN_SCRIPT_LINK_NAME = "RPAScript";
	static final String BEFORE_SCRIPT_LINK_NAME = "Before";
	static final String AFTER_SCRIPT_LINK_NAME = "After";

	private final ZeebeClient zeebeClient;
	private final ZeebeProperties zeebeProperties;
	private final RobotService robotService;
	private final ScriptRepository scriptRepository;
	private final ObjectMapper objectMapper;
	private final SecretsService secretsService;
	private final WorkspaceService workspaceService;

	private final Map<String, JobWorker> jobWorkers = new ConcurrentHashMap<>();

	@Override
	public void onApplicationEvent(ZeebeReadyEvent ignored) {
		doInit();
	}

	ZeebeJobService doInit() {
		jobWorkers.putAll(zeebeProperties.workerTags().stream()
				.map(t -> zeebeProperties.rpaTaskPrefix() + t)
				.collect(Collectors.toMap(subKey -> subKey, this::subscribeTo)));
				
		return this;
	}

	@PreDestroy
	private void destroy() {
		jobWorkers.values().forEach(JobWorker::close);
	}

	private JobWorker subscribeTo(String subKey) {
		log.atInfo()
				.kv("task", subKey)
				.log("Subscribing to task");
		
		return zeebeClient.newWorker()
				.jobType(subKey)
				.handler(createJobHandler(subKey))
				.open();
	}

	private JobHandler createJobHandler(String subKey) {
		return (client, job) -> {
			log.atInfo()
					.kv("task", subKey)
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

			Flux.zip(before.collectList(), main, after.collectList())
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
											workspaceService::deleteWorkspace))
							
							.doOnSuccess(xr -> (switch (xr.result()) {
								case PASS -> client
										.newCompleteCommand(job)
										.variables(xr.outputVariables());

								case FAIL -> client
										.newThrowErrorCommand(job)
										.errorCode("ROBOT_TASKFAIL")
										.errorMessage("There were task failures");

								case ERROR -> client
										.newThrowErrorCommand(job)
										.errorCode("ROBOT_ERROR")
										.errorMessage("There were task errors");
							}).send())
							

							.doOnSuccess(xr -> log.atInfo()
									.kv("task", subKey)
									.kv("job", job.getKey())
									.kv("results", xr.results())
									.log("Job complete")))
					
					.onErrorResume(ProcessTimeoutException.class, 
							_ -> Mono.<ExecutionResults>empty()
									.doOnSubscribe(_ -> client
											.newThrowErrorCommand(job)
											.errorCode("ROBOT_TIMEOUT")
											.errorMessage("The execution timed out")
											.send())
							
									.doOnSubscribe(_ -> log.atWarn()
											.kv("job", job)
											.log("Execution aborted, timeout exceeded")))

					.doOnError(thrown -> log.atError()
							.kv("task", subKey)
							.kv("job", job.getKey())
							.setCause(thrown)
							.log("Error while executing Job"))
					
					.doOnError(thrown -> client
							.newFailCommand(job)
							.retries(job.getRetries())
							.errorMessage(thrown.getMessage())
							.send())

					.onErrorComplete()
					.subscribe();
		};
	}

	private Flux<String> getScriptKeys(ActivatedJob job, String linkName) {
		List<ZeebeLinkedResources.ZeebeLinkedResource> linkedResources = Optional.ofNullable(
						job.getCustomHeaders().get(LINKED_RESOURCES_HEADER_NAME))
				.map(rawHeader -> Try.of(() -> objectMapper.readValue(rawHeader, ZeebeLinkedResources.class)).get())
				.stream()
				.flatMap(rs -> rs.linkedResources().stream())
				.filter(r -> r.resourceType().equals("RPA"))
				.filter(r -> r.linkName().equals(linkName))
				.toList();

		return Flux.fromStream(linkedResources.stream().map(ZeebeLinkedResources.ZeebeLinkedResource::key));
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

}
