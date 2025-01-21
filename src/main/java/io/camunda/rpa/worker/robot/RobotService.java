package io.camunda.rpa.worker.robot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.PythonInterpreter;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.util.YamlMapper;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RobotService {

	static final int ROBOT_TASK_FAILURE_EXIT_CODE = 1;

	private final IO io;
	private final ObjectMapper objectMapper;
	private final PythonInterpreter pythonInterpreter;
	private final ProcessService processService;
	private final YamlMapper yamlMapper;

	private record RobotEnvironment(Path workDir, Path scriptFile, Path varsFile, Path outputDir, Path artifactsDir) { }

	public Mono<ExecutionResult> execute(RobotScript script, Map<String, Object> variables, Map<String, String> secrets) {
		return newRobotEnvironment(script, variables)
				.flatMap(renv -> processService.execute(pythonInterpreter.path(), c -> c

								.workDir(renv.workDir())
								.allowExitCode(ROBOT_TASK_FAILURE_EXIT_CODE)
								.env("ROBOT_ARTIFACTS", renv.artifactsDir().toAbsolutePath().toString())
								.env(secrets)

								.arg("-m").arg("robot")
								.arg("--rpa")
								.arg("--outputdir").bindArg("outputDir", renv.outputDir())
								.arg("--variablefile").bindArg("varsFile", renv.varsFile())
								.arg("--report").arg("none")
								.arg("--logtitle").arg("Task log")
								.bindArg("script", renv.scriptFile()))

						.onErrorMap(thrown -> new RobotFailureException(thrown))

						.zipWhen(_ -> getOutputVariables(renv),
								(xr, vars) -> new ExecutionResult(
										switch (xr.exitCode()) {
											case 0 -> ExecutionResult.Result.PASS;
											case ROBOT_TASK_FAILURE_EXIT_CODE -> ExecutionResult.Result.FAIL;
											default -> ExecutionResult.Result.ERROR;
										},
										mergeOutput(xr.stdout(), xr.stderr()), vars)));
	}

	private Mono<RobotEnvironment> newRobotEnvironment(RobotScript script, Map<String, Object> variables) {
		return io.supply(() -> {
			Path workDir = io.createTempDirectory("robot");
			Path scriptFile = workDir.resolve("script.robot");
			Path varsFile = workDir.resolve("variables.json");
			Path outputDir = workDir.resolve("output");
			io.createDirectories(outputDir);
			Path artifactsDir = workDir.resolve("robot_artifacts");
			io.createDirectories(artifactsDir);

			io.writeString(scriptFile, script.body());
			io.write(varsFile, Try.of(() -> objectMapper.writeValueAsBytes(variables)).get());
			return new RobotEnvironment(workDir, scriptFile, varsFile, outputDir, artifactsDir);
		});
	}

	private Mono<Map<String, Object>> getOutputVariables(RobotEnvironment robotEnvironment) {
		return io.supply(() -> {
			Path outputs = robotEnvironment.workDir().resolve("outputs.yml");
			if (io.notExists(outputs)) return Collections.emptyMap();
			return io.withReader(outputs, r -> yamlMapper.readValue(r, new TypeReference<Map<String, Object>>() {}));
		});
	}

	private String mergeOutput(String stdout, String stderr) {
		return Stream.concat(
						stdout.lines().map("[STDOUT] %s"::formatted),
						stderr.lines().map("[STDERR] %s"::formatted))
				.collect(Collectors.joining("\n"));
	}
}
