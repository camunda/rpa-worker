package io.camunda.rpa.worker.python;

import io.camunda.rpa.worker.io.IO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExistingEnvironmentProvider {
	
	private static final PythonSetupService.PythonExecutionEnvironment pyExeEnv = PythonSetupService.pyExeEnv;

	private final PythonProperties pythonProperties;
	private final IO io;

	public Optional<Path> existingPythonEnvironment() {
		if (io.notExists(pythonProperties.path().resolve("venv/pyvenv.cfg")))
			return Optional.empty();

		return Optional.of(pythonProperties.path()
				.resolve("venv/")
				.resolve(pyExeEnv.binDir())
				.resolve(pyExeEnv.pythonExe()));
	}

}
