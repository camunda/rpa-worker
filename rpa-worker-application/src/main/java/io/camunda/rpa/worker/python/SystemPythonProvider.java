package io.camunda.rpa.worker.python;

import com.github.zafarkhaja.semver.Version;
import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SystemPythonProvider {

	static final Set<Integer> WINDOWS_NO_PYTHON_EXIT_CODES = Set.of(49, 9009);

	private static final Version MINIMUM_PYTHON_VERSION = Version.of(3, 10);
	private static final Version MAXIMUM_PYTHON_VERSION = Version.of(3, 13);
	private static final Pattern PYTHON_VERSION_PATTERN = Pattern.compile("Python (?<version>[0-9a-zA-Z-.+]+)");

	private final PythonProperties pythonProperties;
	private final ProcessService processService;
	@Getter
	private final Mono<Object> systemPython;

	SystemPythonProvider(PythonProperties pythonProperties, ProcessService processService) {
		this.pythonProperties = pythonProperties;
		this.processService = processService;
		this.systemPython = findSystemPython().cache();
	}

	private Mono<Object> findSystemPython() {
		return Mono.<Object>justOrEmpty(pythonProperties.interpreter())
				.flatMap(p -> checkPythonInterpreter(p, ExecutionCustomizer::required).map(_ -> p))
				.flux()
				.switchIfEmpty(Flux.<Object>just("python3", "python")
						.concatMap(exeName -> checkPythonInterpreter(exeName, ExecutionCustomizer::silent)
								.onErrorComplete(IOException.class)
								.map(_ -> exeName))
						.next())
				.singleOrEmpty();
	}

	private Mono<ProcessService.ExecutionResult> checkPythonInterpreter(Object exeName, UnaryOperator<ExecutionCustomizer> customizer) {
		log.atInfo().kv("exeName", exeName).log("checkPythonInterpreter");
		return processService.execute(exeName, c -> customizer.apply(c).arg("--version"))
				.filter(xr -> ! WINDOWS_NO_PYTHON_EXIT_CODES.contains(xr.exitCode()))
				.filter(xr -> {
					Matcher matcher = PYTHON_VERSION_PATTERN.matcher(xr.stdout());
					boolean found = matcher.find();
					if ( ! found) return false;
					Version version = Version.parse(matcher.group("version"));

					boolean valid = version.isHigherThanOrEquivalentTo(MINIMUM_PYTHON_VERSION) && (version.isLowerThan(MAXIMUM_PYTHON_VERSION) || pythonProperties.allowUnsupportedPython());
					if ( ! valid) log.atWarn()
							.kv("version", version)
							.kv("interpreter", exeName)
							.log("Python interpreter is not valid (>=%s,<%s)".formatted(MINIMUM_PYTHON_VERSION, MAXIMUM_PYTHON_VERSION));
					else log.atInfo()
							.kv("version", version)
							.kv("interpreter", exeName)
							.log("Python interpreter is valid");

					return valid;
				})
				.flatMap(_ -> processService.execute(exeName, c -> c
								.arg("-m").arg("venv")
								.silent()
								.required()
								.allowExitCodes(new int[]{0, 2}))
						.doOnError(_ -> log.atWarn()
								.kv("interpreter", exeName)
								.log("Discovered Python interpreter does not provide VEnv. If this Python is managed by the system package manager you may need to install the 'python3-venv' package")));
	}
}
