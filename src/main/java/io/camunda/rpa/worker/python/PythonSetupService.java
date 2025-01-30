package io.camunda.rpa.worker.python;

import com.github.zafarkhaja.semver.Version;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PythonSetupService implements FactoryBean<PythonInterpreter> {
	
	static final Set<Integer> WINDOWS_NO_PYTHON_EXIT_CODES = Set.of(49, 9009);
	
	private static final Version MINIMUM_PYTHON_VERSION = Version.of(3, 8);
	private static final Pattern PYTHON_VERSION_PATTERN = Pattern.compile("Python (?<version>[0-9a-zA-Z-.+]+)");

	static final PythonExecutionEnvironment pyExeEnv = PythonExecutionEnvironment.get();
	
	private final PythonProperties pythonProperties;
	private final IO io;
	private final ProcessService processService;
	private final WebClient webClient;

	@Override
	public Class<?> getObjectType() {
		return PythonInterpreter.class;
	}

	@Override
	public PythonInterpreter getObject() {
		return Mono.justOrEmpty(existingPythonEnvironment())
				.switchIfEmpty(Mono.defer(this::createPythonEnvironment))
				.map(PythonInterpreter::new)
				.doOnNext(pi -> log.atInfo()
						.kv("path", pi.path().toAbsolutePath())
						.log("Using Python interpreter"))
				.block();
	}

	private Optional<Path> existingPythonEnvironment() {
		if (io.notExists(pythonProperties.path().resolve("pyvenv.cfg")))
			return Optional.empty();

		return Optional.of(pythonProperties.path().resolve(pyExeEnv.binDir()).resolve(pyExeEnv.pythonExe()));
	}

	private Mono<Path> createPythonEnvironment() {
		return systemPython()
				.switchIfEmpty(Mono.defer(this::installPython))
				.flatMap(pathOrString -> processService.execute(pathOrString, c -> c
								.arg("-m").arg("venv")
								.bindArg("pyEnvPath", pythonProperties.path())
								.inheritEnv())

						.doOnSubscribe(_ -> log.atInfo()
								.kv("dir", pythonProperties.path())
								.log("Creating new Python environment")))

				.map(_ -> pythonProperties.path().resolve(pyExeEnv.binDir()))
				.flatMap(pyBinDir ->
						writeRequirements().flatMap(requirements ->
								processService.execute(pyBinDir.resolve(pyExeEnv.pipExe()), c -> c
												.arg("install")
												.arg("-r").bindArg("requirementsTxt", requirements)
												.inheritEnv())

										.doOnSubscribe(_ ->
												log.atInfo().log("Installing Pip dependencies"))))

				.thenReturn(pythonProperties.path().resolve(pyExeEnv.binDir()).resolve(pyExeEnv.pythonExe()));
	}

	private Mono<Object> systemPython() {
		return Flux.<Object>just("python3", "python")
				.flatMap(exeName -> processService.execute(exeName, c -> c.arg("--version"))
						.onErrorComplete(IOException.class)
						.filter(xr -> ! WINDOWS_NO_PYTHON_EXIT_CODES.contains(xr.exitCode()))
						.filter(xr -> {
							Matcher matcher = PYTHON_VERSION_PATTERN.matcher(xr.stdout());
							matcher.find();
							Version version = Version.parse(matcher.group("version"));
							return version.isHigherThan(MINIMUM_PYTHON_VERSION);
						})
						.map(_ -> exeName))
				.next();
	}

	private Mono<Object> installPython() {

		if ( ! System.getProperty("os.name").contains("Windows"))
			return Mono.error(new RuntimeException("No suitable Python installation was found, and automatic installation is not supported on this platform"));

		Path basePythonDir = io.createTempDirectory("python");
		Path pythonArchive = io.createTempFile("python", ".zip");

		return webClient.get()
				.uri(pythonProperties.downloadUrl())
				.retrieve()
				.bodyToFlux(DataBuffer.class)
				.as(buffers -> io.write(buffers, pythonArchive))

				.doOnSubscribe(_ -> log.atInfo().log("Downloading Python"))

				.then(io.run(() -> extractArchive(pythonArchive, basePythonDir))
						.doOnSubscribe(_ -> log.atInfo()
								.kv("archive", pythonArchive.toString())
								.kv("dest", basePythonDir)
								.log("Extracting Python archive")))

				.then(io.supply(() -> io.list(basePythonDir)
						.findFirst()
						.map(it -> it.resolve("python/").resolve(pyExeEnv.pythonExe()))
						.orElseThrow()));
	}
	
	private void extractArchive(Path archive, Path destination) {
		io.doWithFileSystem(archive, zip -> {
			Path root = zip.getPath("/");
			io.walk(root)
					.filter(io::isRegularFile)
					.forEach(p -> {
						io.createDirectories(destination.resolve(root.relativize(p).getParent().toString()));
						io.copy(p, destination.resolve(root.relativize(p).toString()));
					});
		});
	}

	private Mono<Path> writeRequirements() {
		return io.supply(() -> {
			Path requirementsTxt = io.createTempFile("python_requirements", ".txt");
			io.copy(getClass().getClassLoader().getResourceAsStream("python/requirements.txt"), requirementsTxt, StandardCopyOption.REPLACE_EXISTING);
			return requirementsTxt;
		});
	}

	record PythonExecutionEnvironment(Path binDir, String exeSuffix) {

		public static PythonExecutionEnvironment get() {
			if (System.getProperty("os.name").contains("Windows"))
				return new PythonExecutionEnvironment(Paths.get("Scripts/"), ".exe");
			return new PythonExecutionEnvironment(Paths.get("bin/"), "");
		}

		public String pythonExe() {
			return "python" + exeSuffix();
		}

		public String pipExe() {
			return "pip" + exeSuffix();
		}
	}
}
