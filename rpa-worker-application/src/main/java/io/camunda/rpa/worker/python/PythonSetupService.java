package io.camunda.rpa.worker.python;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Callable;

import static io.camunda.rpa.worker.util.ArchiveUtils.extractArchive;

@Service
@RequiredArgsConstructor
@Slf4j
public class PythonSetupService {
	
	static final PythonExecutionEnvironment pyExeEnv = PythonExecutionEnvironment.get();
	
	private final PythonProperties pythonProperties;
	private final IO io;
	private final ProcessService processService;
	private final WebClient webClient;
	private final ExistingEnvironmentProvider existingEnvironmentProvider;
	private final SystemPythonProvider systemPythonProvider;

	Mono<PythonInterpreter> getPythonInterpreter() {
		return Mono.justOrEmpty(existingEnvironmentProvider.existingPythonEnvironment())
				.flatMap(p -> maybeReinstallBaseRequirements().thenReturn(p))
				.flatMap(p -> maybeReinstallExtraRequirements().thenReturn(p))
				.switchIfEmpty(Mono.defer(this::createPythonEnvironment))
				.map(PythonInterpreter::new)
				.doOnNext(pi -> log.atInfo()
						.kv("path", pi.path().toAbsolutePath())
						.log("Using Python interpreter"));
	}

	private Mono<Path> createPythonEnvironment() {
		return systemPythonProvider.systemPython()
				.switchIfEmpty(Mono.defer(this::installPython))
				.flatMap(pathOrString -> processService.execute(pathOrString, c -> c
								.arg("-m").arg("venv")
								.bindArg("pyEnvPath", pythonProperties.path().resolve("venv/"))
								.inheritEnv())

						.doOnSubscribe(_ -> log.atInfo()
								.kv("dir", pythonProperties.path())
								.log("Creating new Python environment")))
				
				.then(maybeReinstallBaseRequirements())
				.then(maybeReinstallExtraRequirements())
				.thenReturn(pythonProperties.path().resolve("venv/").resolve(pyExeEnv.binDir()).resolve(pyExeEnv.pythonExe()));
	}

	private Mono<Object> installPython() {

		if ( ! System.getProperty("os.name").contains("Windows"))
			return Mono.error(new RuntimeException("No suitable Python installation was found, and automatic installation is not supported on this platform"));

		Path pythonArchive = io.createTempFile("python", ".zip");

		return webClient.get()
				.uri(pythonProperties.downloadUrl())
				.retrieve()
				.bodyToFlux(DataBuffer.class)
				.as(buffers -> writeWithIntegrityCheck(buffers, pythonArchive, pythonProperties.downloadHash()))

				.doOnSubscribe(_ -> log.atInfo().log("Downloading Python"))

				.then(io.run(() -> extractArchive(io, pythonArchive, pythonProperties.path()))
						.doOnSubscribe(_ -> log.atInfo()
								.kv("archive", pythonArchive.toString())
								.kv("dest", pythonProperties.path())
								.log("Extracting Python archive")))

				.then(io.supply(() -> io.list(pythonProperties.path())
						.findFirst()
						.map(it -> it.resolve("python/").resolve(pyExeEnv.pythonExe()))
						.orElseThrow()));
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

	private Mono<String> writeWithChecksum(InputStream contents, Path destination) {
		MessageDigest md = Try.of(() -> MessageDigest.getInstance("sha-256")).get();

		return Mono.using(() -> new BufferedOutputStream(io.newOutputStream(destination)),
				fileOut -> Mono.using(() -> new DigestOutputStream(fileOut, md),
						digestOut -> Mono.fromRunnable(() -> io.transferTo(contents, digestOut))
								.then(Mono.fromSupplier(() -> HexFormat.of().formatHex(digestOut.getMessageDigest().digest())))));
	}

	private Mono<Void> writeWithIntegrityCheck(Flux<DataBuffer> dataBuffers, Path destination, String expected) {
		MessageDigest md = Try.of(() -> MessageDigest.getInstance("sha-256")).get();

		return Flux.using(() -> new BufferedOutputStream(io.newOutputStream(destination)),
						fileOut -> Flux.using(() -> new DigestOutputStream(fileOut, md),
								digestOut -> io.write(dataBuffers, digestOut)
										.doOnNext(DataBufferUtils.releaseConsumer())

										.then(Mono.fromSupplier(() -> HexFormat.of().formatHex(digestOut.getMessageDigest().digest()))
												.flatMap(hash -> hash.equals(expected)
														? Mono.empty()
														: Mono.error(new IllegalStateException())
																.doOnSubscribe(_ -> log.atError()
																		.kv("expected", expected)
																		.kv("actual", hash)
																		.log("Integrity check failed for Python download"))))))
				.then();
	}

	private Mono<Void> maybeReinstallBaseRequirements() {
		return maybeReinstallRequirements("requirements",
				() -> getClass().getClassLoader().getResourceAsStream(pythonProperties.requirementsName()))
				.doOnSubscribe(_ -> log.atInfo().log("Checking base requirements"));
	}

	private Mono<Void> maybeReinstallExtraRequirements() {
		if (pythonProperties.extraRequirements() == null)
			return Mono.<Void>empty()
					.doOnSubscribe(_ -> log.atInfo().log("There are no extra requirements configured"));

		return maybeReinstallRequirements("extra-requirements", () -> io.newInputStream(pythonProperties.extraRequirements()))
				.doOnSubscribe(_ -> log.atInfo()
						.kv("source", pythonProperties.extraRequirements())
						.log("Checking extra requirements"));
	}

	private Mono<Void> maybeReinstallRequirements(String requirementsSetName, Callable<InputStream> requirementsContents) {
		Path lastChecksumFile = pythonProperties.path().resolve("%s.last".formatted(requirementsSetName));

		return io.supply(() -> io.createTempFile(requirementsSetName, ".txt"))
				.flatMap(requirementsDst -> Mono.using(requirementsContents, is -> writeWithChecksum(is, requirementsDst))
						.filter(newChecksum -> io.notExists(lastChecksumFile)
								|| ! io.readString(lastChecksumFile).equals(newChecksum))

						.doOnNext(_ -> log.atInfo()
								.kv("requirements", requirementsSetName)
								.log("Requirements install is necessary"))

						.switchIfEmpty(Mono.<String>empty().doOnSubscribe(_ -> log.atInfo()
								.kv("requirements", requirementsSetName)
								.log("Requirements install is not necessary")))

						.flatMap(newExRequirementsChecksum -> 
								installRequirements(requirementsSetName, requirementsDst, newExRequirementsChecksum)));
	}
		
	private Mono<Void> installRequirements(String requirementsSetName, Path requirementsFile, String requirementsFileChecksum) {
		Path lastChecksumFile = pythonProperties.path().resolve("%s.last".formatted(requirementsSetName));
		return io.run(() -> io.deleteIfExists(lastChecksumFile))
				.then(installWithPip(requirementsFile)
						.doOnSuccess(_ -> io.writeString(lastChecksumFile, requirementsFileChecksum)))
				.then();
	}

	private Mono<ProcessService.ExecutionResult> installWithPip(Path requirements) {
		return processService.execute(pythonProperties.path()
						.resolve("venv/")
						.resolve(pyExeEnv.binDir())
						.resolve(pyExeEnv.pipExe()),
				c -> c
						.arg("install")
						.arg("-r").bindArg("requirementsTxt", requirements)
						.inheritEnv()
						.required())
				.doOnSubscribe(_ -> log.atInfo()
						.kv("requirements", requirements)
						.log("Installing Python requirements"))
				.onErrorMap(thrown -> new PipFailureException(thrown));
	}

	public Mono<Void> purgeEnvironment() {
		return io.run(() -> io.deleteDirectoryRecursively(pythonProperties.path()));
	}
}
