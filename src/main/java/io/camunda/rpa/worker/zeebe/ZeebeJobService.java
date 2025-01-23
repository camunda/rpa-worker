package io.camunda.rpa.worker.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.script.ScriptRepository;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.vavr.control.Try;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class ZeebeJobService {
	
	static final String LINKED_RESOURCES_HEADER_NAME = "camunda::linkedResources";

	private final ZeebeClient zeebeClient;
	private final ZeebeProperties zeebeProperties;
	private final RobotService robotService;
	private final ScriptRepository scriptRepository;
//	private final SecretsService secretsService;
	private final ObjectMapper objectMapper;

	private final Map<String, JobWorker> jobWorkers = new ConcurrentHashMap<>();

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

			Mono.fromSupplier(() -> getScriptKey(job))
					.flatMap(scriptKey -> scriptRepository.findById(scriptKey)
							.flatMap(script -> getSecretsAsEnv(job)
									.flatMap(secrets ->
											robotService.execute(script, getVariables(job), secrets)))

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
									.kv("result", xr.result())
									.log("Job complete")))

					.doOnError(thrown -> client
							.newFailCommand(job)
							.retries(job.getRetries())
							.errorMessage(thrown.getMessage())
							.send())

					.doOnError(thrown -> log.atError()
							.kv("task", subKey)
							.kv("job", job.getKey())
							.setCause(thrown)
							.log("Error while executing Job"))

					.subscribe();
		};
	}

	private String getScriptKey(ActivatedJob job) {
		List<ZeebeLinkedResources.ZeebeLinkedResource> linkedResources = Optional.ofNullable(
						job.getCustomHeaders().get(LINKED_RESOURCES_HEADER_NAME))
				.map(rawHeader -> Try.of(() -> objectMapper.readValue(rawHeader, ZeebeLinkedResources.class)).get())
				.stream()
				.flatMap(rs -> rs.linkedResources().stream())
				.filter(r -> r.resourceType().equals("RPA"))
				.filter(r -> r.linkName().equals("RPAScript"))
				.toList();

		if (linkedResources.size() != 1)
			throw new IllegalStateException("Expected to find exactly 1 LinkedResource providing the RPA Script, found %s [%s]".formatted(
					linkedResources.size(),
					linkedResources));
		
		return linkedResources.getFirst().key();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getVariables(ActivatedJob job) {
		return Optional.ofNullable(((Map<String, Object>) job.getVariablesAsMap().get("camundaRpaTaskInput")))
				.orElse(job.getVariablesAsMap());
	}

	private Mono<Map<String, String>> getSecretsAsEnv(ActivatedJob job) {
		return Mono.just(Collections.<String, String>emptyMap()) // TODO: No Secrets yet
				.map(m -> m.entrySet().stream()
						.map(kv -> Map.entry(
								"SECRET_%s".formatted(kv.getKey().toUpperCase()),
								kv.getValue()))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
	}
}
