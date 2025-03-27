package io.camunda.rpa.worker.robot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.pexec.ProcessTimeoutException;
import io.camunda.rpa.worker.python.PythonInterpreter;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.util.MoreCollectors;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import io.camunda.rpa.worker.workspace.WorkspaceVariablesManager;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RobotService {

	static final int ROBOT_EXIT_INTERNAL_ERROR = 255;
	static final int ROBOT_EXIT_INTERRUPTED = 253;
	static final int ROBOT_EXIT_INVALID_INVOKE = 252;
	static final int ROBOT_EXIT_HELP_OR_VERSION_REQUEST = 251;
	static final int[] ROBOT_TASK_FAILURE_EXIT_CODES = IntStream.rangeClosed(1, 250).toArray();
	static final int ROBOT_EXIT_SUCCESS = 0;

	private final IO io;
	private final ObjectMapper objectMapper;
	private final PythonInterpreter pythonInterpreter;
	private final ProcessService processService;
	private final RobotProperties robotProperties;
	private final WorkspaceService workspaceService;
	private final Scheduler robotWorkScheduler;
	private final ObjectProvider<EnvironmentVariablesContributor> environmentContributors;
	private final WorkspaceVariablesManager workspaceVariablesManager;

	private record RobotEnvironment(Workspace workspace, Path varsFile, Path outputDir, Path artifactsDir) { }

	public Mono<ExecutionResults> execute(
			RobotScript script,
			Map<String, Object> variables,
			Duration timeout,
			List<RobotExecutionListener> executionListeners, 
			String workspaceAffinityKey) {
		
		return execute(script, Collections.emptyList(), Collections.emptyList(), variables, timeout, executionListeners, Collections.emptyMap(), workspaceAffinityKey);
	}
	
	public Mono<ExecutionResults> execute(
			RobotScript script,
			List<RobotScript> beforeScripts,
			List<RobotScript> afterScripts,
			Map<String, Object> variables,
			Duration timeout,
			List<RobotExecutionListener> executionListeners,
			Map<String, Object> workspaceProperties, 
			String workspaceAffinityKey) {

		AtomicInteger beforeCounter = new AtomicInteger(0);
		AtomicInteger afterCounter = new AtomicInteger(0);

		List<PreparedScript> scripts = Stream.of(
						beforeScripts.stream()
								.map(s -> new PreparedScript("pre_%s_%s".formatted(beforeCounter.getAndIncrement(), s.id()), s)),

						Stream.of(script)
								.map(s -> new PreparedScript("main", s)),

						afterScripts.stream()
								.map(s -> new PreparedScript("post_%s_%s".formatted(afterCounter.getAndIncrement(), s.id()), s)))

				.flatMap(s -> s)
				.toList();

		
		List<RobotExecutionListener> allListeners = Stream.concat(Stream.of(workspaceVariablesManager), executionListeners.stream()).toList();
		return doExecute(scripts, variables, timeout != null ? timeout : robotProperties.defaultTimeout(), allListeners, workspaceProperties, workspaceAffinityKey);
	}
	
	private Mono<ExecutionResults> doExecute(
			List<PreparedScript> scripts,
			Map<String, Object> variables,
			Duration timeout,
			List<RobotExecutionListener> executionListeners,
			Map<String, Object> workspaceProperties, 
			String workspaceAffinityKey) {

		return newRobotEnvironment(scripts, variables, workspaceProperties, workspaceAffinityKey)
				.flatMap(renv ->
						Flux.fromIterable(scripts)
								.concatMap(script -> getEnvironmentVariables(renv, script)

										.flatMap(envVars ->
												executeRobot(timeout, executionListeners, renv, script, envVars)))

								.onErrorResume(RobotFailureException.class, thrown ->
										Mono.just(thrown.getExecutionResult()))

								.onErrorMap(thrown -> ! (thrown instanceof ProcessTimeoutException),
										thrown -> new RobotErrorException(thrown))

								.collect(MoreCollectors.toSequencedMap(
										ExecutionResults.ExecutionResult::executionId,
										kv -> kv,
										MoreCollectors.MergeStrategy.noDuplicatesExpected()))

								.doFinally(_ -> executionListeners.forEach(
										l -> l.afterRobotExecution(renv.workspace())))

								.map(resultsMap -> new ExecutionResults(
										resultsMap,
										getWorstCase(resultsMap.values()),
										resultsMap.values().stream()
												.flatMap(s -> s.outputVariables().entrySet().stream())
												.collect(MoreCollectors.toSequencedMap(
														Map.Entry::getKey,
														Map.Entry::getValue,
														MoreCollectors.MergeStrategy.rightPrecedence())),
										renv.workspace().path(), 
										resultsMap.values().stream()
												.map(ExecutionResults.ExecutionResult::duration)
												.reduce(Duration.ZERO, Duration::plus))));
	}

	private Mono<RobotEnvironment> newRobotEnvironment(
			List<PreparedScript> scripts, 
			Map<String, Object> variables, 
			Map<String, Object> workspaceProperties, 
			String workspaceAffinityKey) {
		
		return io.supply(() -> {
			Workspace workspace = workspaceService.createWorkspace(workspaceAffinityKey, workspaceProperties);
			Path varsFile = workspace.path().resolve("variables.json");
			Path outputDir = workspace.path().resolve("output");
			io.createDirectories(outputDir);
			Path artifactsDir = workspace.path().resolve("robot_artifacts");
			io.createDirectories(artifactsDir);

			scripts.forEach(s -> io.writeString(
					workspace.path().resolve("%s.robot".formatted(s.executionKey())),
					s.script().body()));
			io.write(varsFile, Try.of(() -> objectMapper.writeValueAsBytes(variables)).get());
			
			return new RobotEnvironment(workspace, varsFile, outputDir, artifactsDir);
		});
	}

	private Mono<Map<String, String>> getEnvironmentVariables(RobotEnvironment renv, PreparedScript script) {
		return Flux.fromStream(environmentContributors.stream())
				.flatMap(ec -> ec.getEnvironmentVariables(renv.workspace(), script))
				.flatMapIterable(Map::entrySet)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private Mono<ExecutionResults.ExecutionResult> executeRobot(
			Duration timeout,
			List<RobotExecutionListener> executionListeners,
			RobotEnvironment renv,
			PreparedScript script,
			Map<String, String> envVars) {

		return processService.execute(pythonInterpreter.path(), c -> c

						.workDir(renv.workspace().path())
						.allowExitCodes(ROBOT_TASK_FAILURE_EXIT_CODES)

						.inheritEnv()
						.env(envVars)

						.arg("-m").arg("robot")
						.arg("--rpa")
						.arg("--outputdir").bindArg("outputDir", renv.outputDir().resolve(script.executionKey()))
						.arg("--variablefile").bindArg("varsFile", renv.varsFile())
						.arg("--report").arg("none")
						.arg("--logtitle").arg("Task log")
						.conditionalArg(robotProperties::failFast, "-X")
						.bindArg("script", renv.workspace().path().resolve("%s.robot".formatted(script.executionKey())))

						.timeout(timeout)
						.scheduleOn(robotWorkScheduler))
				
				.doOnSubscribe(_ -> executionListeners.forEach(l -> 
						l.beforeScriptExecution(renv.workspace(), timeout)))

				.map(xr -> toRobotExecutionResult(
								script.executionKey(),
								xr,
								getOutputVariables(renv)))

				.flatMap(xr -> xr.result() != ExecutionResults.Result.PASS
						? Mono.error(new RobotFailureException(xr))
						: Mono.just(xr));
	}

	private Map<String, Object> getOutputVariables(RobotEnvironment robotEnvironment) {
		return workspaceVariablesManager.getVariables(robotEnvironment.workspace().id());
	}

	private String mergeOutput(String stdout, String stderr) {
		return Stream.concat(
						stdout.lines().map("[STDOUT] %s"::formatted),
						stderr.lines().map("[STDERR] %s"::formatted))
				.collect(Collectors.joining("\n"));
	}
	
	private ExecutionResults.ExecutionResult toRobotExecutionResult(
			String executionId, 
			ProcessService.ExecutionResult xr, 
			Map<String, Object> outputVariables) {
		
		return new ExecutionResults.ExecutionResult(executionId, switch (xr.exitCode()) {
			case ROBOT_EXIT_SUCCESS -> ExecutionResults.Result.PASS;

			case ROBOT_EXIT_INTERNAL_ERROR,
			     ROBOT_EXIT_INTERRUPTED,
			     ROBOT_EXIT_INVALID_INVOKE -> ExecutionResults.Result.ERROR;

			default -> ExecutionResults.Result.FAIL;
		}, mergeOutput(xr.stdout(), xr.stderr()), outputVariables, xr.duration());
	}

	private ExecutionResults.Result getWorstCase(Collection<ExecutionResults.ExecutionResult> results) {
		return results.stream()
				.map(ExecutionResults.ExecutionResult::result)
				.max(Comparator.naturalOrder())
				.orElseThrow(() -> new NoSuchElementException("The result set contained no results"));
	}
}
