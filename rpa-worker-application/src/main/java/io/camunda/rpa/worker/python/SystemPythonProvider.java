package io.camunda.rpa.worker.python;

import com.github.zafarkhaja.semver.Version;
import io.camunda.rpa.worker.pexec.ProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemPythonProvider {
	
	static final Set<Integer> WINDOWS_NO_PYTHON_EXIT_CODES = Set.of(49, 9009);

	private static final Version MINIMUM_PYTHON_VERSION = Version.of(3, 8);
	private static final Version MAXIMUM_PYTHON_VERSION = Version.of(3, 13);
	private static final Pattern PYTHON_VERSION_PATTERN = Pattern.compile("Python (?<version>[0-9a-zA-Z-.+]+)");

	private final PythonProperties pythonProperties;
	private final ProcessService processService;
	
	public Mono<Object> systemPython() {
		return Mono.<Object>justOrEmpty(pythonProperties.interpreter())
				.doOnNext(this::checkPythonInterpreter)
				.flux()
				.switchIfEmpty(Flux.<Object>just("python3", "python")
						.flatMap(exeName -> checkPythonInterpreter(exeName)
								.map(_ -> exeName)))
				.next();
	}

	private Mono<ProcessService.ExecutionResult> checkPythonInterpreter(Object exeName) {
		return processService.execute(exeName, c -> c.silent().arg("--version"))
				.onErrorComplete(IOException.class)
				.filter(xr -> ! WINDOWS_NO_PYTHON_EXIT_CODES.contains(xr.exitCode()))
				.filter(xr -> {
					Matcher matcher = PYTHON_VERSION_PATTERN.matcher(xr.stdout());
					boolean found = matcher.find();
					if ( ! found) return false;
					Version version = Version.parse(matcher.group("version"));
					boolean valid = version.isHigherThanOrEquivalentTo(MINIMUM_PYTHON_VERSION) && (version.isLowerThan(MAXIMUM_PYTHON_VERSION) || pythonProperties.allowUnsupportedPython());
					if( ! valid) log.atWarn()
							.kv("version", version)
							.kv("interpreter", exeName)
							.log("Python interpreter is not valid (>=%s,<%s)".formatted(MINIMUM_PYTHON_VERSION, MAXIMUM_PYTHON_VERSION));
					return valid;
				});
	}
}
