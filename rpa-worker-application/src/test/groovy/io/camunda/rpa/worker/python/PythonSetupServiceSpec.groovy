package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.FileHashUtils
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import org.apache.commons.exec.CommandLine
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

import java.nio.channels.Channels
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.UnaryOperator
import java.util.stream.Stream

class PythonSetupServiceSpec extends Specification implements PublisherUtils {
	
	private static final String ZERO_DATA_SHA_256_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	private static final String REAL_BASE_REQUIREMENTS_SHA_256_HASH =
			FileHashUtils.hashFile(PythonSetupServiceSpec.classLoader.getResource("python/requirements.txt").text)
	private static final String STUB_EXTRA_REQUIREMENTS_SHA_256_HASH = "0a106a4361167bf5f9650af8385e7ac01d836841db65bc909c4b5713879eb843"

	PythonProperties pythonProperties = PythonProperties.builder()
			.path(Paths.get("/path/to/python/"))
			.downloadUrl("https://python/python".toURI())
			.downloadHash(ZERO_DATA_SHA_256_HASH)
			.requirementsName("python/requirements.txt")
			.build()
	
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
		run(_) >> { Runnable fn -> Mono.fromRunnable(fn) }
	}
	ProcessService processService = Mock()
	WebClient webClient = Mock()
	ExistingEnvironmentProvider existingEnvironmentProvider = Mock()
	SystemPythonProvider systemPythonProvider = Mock()
	
	@Subject
	PythonSetupService service = new PythonSetupService(
			pythonProperties, 
			io, 
			processService, 
			webClient, 
			existingEnvironmentProvider, 
			systemPythonProvider)

	void setupSpec() {
		CommandLine.metaClass.equals = { CommandLine other ->
			CommandLine thiz = delegate as CommandLine
			return thiz.file == other.file
					&& thiz.arguments == other.arguments
					&& thiz.executable == other.executable
		}
	}

	void "Returns existing environment if available"() {
		given:
		Path pythonPath = pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pythonExe()))
		
		when:
		PythonInterpreter r = block service.getPythonInterpreter()
		
		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(pythonPath)
		0 * systemPythonProvider.systemPython()
		
		and:
		r.path() == pythonPath
		
		and:
		1 * io.supply(_) >> Mono.empty()
		0 * io._(*_)
	}
	
	void "Creates new environment using system Python"() {
		given:
		processService.execute("python3", _) >> Mono.error(new IOException())
		
		when:
		PythonInterpreter r = block service.getPythonInterpreter()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.just("python")

		and:
		1 * processService.execute("python", _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * arg("-m") >> it
				1 * arg("venv") >> it
				1 * bindArg("pyEnvPath", pythonProperties.path().resolve("venv/")) >> it
				1 * inheritEnv() >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}
		
		and:
		1 * io.createTempFile("requirements", ".txt") >> Paths.get("/tmp/requirements.txt")
		1 * io.notExists(Paths.get("/path/to/python/requirements.last")) >> true
		1 * io.newOutputStream(_) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> 0L
		1 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * arg("install") >> it
				1 * arg("-r") >> it
				1 * bindArg("requirementsTxt", Paths.get("/tmp/requirements.txt")) >> it
				1 * inheritEnv() >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}

		and:
		r.path() == pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pythonExe()))
	}

	void "Creates new environment using system Python (exe name custom from properties)"() {
		Path interpreter = Paths.get("/my/custom/interpreter")
		given:
		@Subject PythonSetupService serviceForCustomPythonInterp = new PythonSetupService(
				pythonProperties.toBuilder().interpreter(interpreter).build(), 
				io, 
				processService, 
				webClient, 
				existingEnvironmentProvider, 
				systemPythonProvider)

		when:
		PythonInterpreter r = block serviceForCustomPythonInterp.getPythonInterpreter()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.just(interpreter)

		and:
		1 * processService.execute(interpreter, _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * arg("-m") >> it
				1 * arg("venv") >> it
				1 * bindArg("pyEnvPath", pythonProperties.path().resolve("venv/")) >> it
				1 * inheritEnv() >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}

		and:
		1 * io.createTempFile("requirements", ".txt") >> Paths.get("/tmp/requirements.txt")
		1 * io.notExists(Paths.get("/path/to/python/requirements.last")) >> true
		1 * io.newOutputStream(_) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> 0L
		1 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * arg("install") >> it
				1 * arg("-r") >> it
				1 * bindArg("requirementsTxt", Paths.get("/tmp/requirements.txt")) >> it
				1 * inheritEnv() >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}

		and:
		r.path() == pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pythonExe()))
	}

	@RestoreSystemProperties
	void "Downloads Python if no system Python available"() {
		given:
		System.setProperty("os.name", "Windows")
		
		and:
		Path pythonArchive = Paths.get("/tmp/python.zip")
		Path pythonArchiveRootDir = Paths.get("/")
		FileSystem pythonArchiveFs = Stub() {
			getPath("/") >> pythonArchiveRootDir
		}

		when:
		PythonInterpreter r = block service.getPythonInterpreter()

		then: "Existing Python venv is checked (not there) and system Python is checked (not there)"
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.empty()
		
		and: "The standalone Python archive is downloaded from the configured URL"
		1 * io.createTempFile(_, ".zip") >> pythonArchive
		
		1 * webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			1 * uri(pythonProperties.downloadUrl()) >> Mock(WebClient.RequestHeadersSpec) {
				1 * retrieve() >> Mock(WebClient.ResponseSpec) {
					1 * bodyToFlux(DataBuffer.class) >> Flux.empty()
				}
			}
		}

		1 * io.newOutputStream(pythonArchive) >> Stub(OutputStream)
		1 * io.write({ it }, _) >> Flux.empty()
		
		and: "The downloaded Python archive is extracted"
		1 * io.doWithFileSystem(_, _, _) >> { __, ___, Consumer<FileSystem> fn -> fn.accept(pythonArchiveFs) }
		1 * io.walk(pythonArchiveRootDir) >> Stream.of(
				Paths.get("/aDir/anotherDir/aFile"),
				Paths.get("/aDir/differentDir/anotherFile"))
		io.isRegularFile(_, _) >> true

		1 * io.createDirectories(pythonProperties.path().resolve("aDir/anotherDir/"))
		1 * io.copy(_, pythonProperties.path().resolve("aDir/anotherDir/aFile"))
		1 * io.createDirectories(pythonProperties.path().resolve("aDir/differentDir/"))
		1 * io.copy(_, pythonProperties.path().resolve("aDir/differentDir/anotherFile"))
		
		1 * io.list(pythonProperties.path()) >> Stream.of(Paths.get("aDir/"))
		
		and:
		r.path() == pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pythonExe()))

		then:
		processService.execute(_, _) >> Mono.empty()
	}

	void "Installs user requirements into new environments when provided"() {
		given:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		systemPythonProvider.systemPython() >> Mono.just("python3")
		processService.execute("python3", _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Stub(ExecutionCustomizer) {
				arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.12.8", "", Duration.ZERO))
		}
		
		and:
		processService.execute("python3", _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Stub(ExecutionCustomizer) {
				_ >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}
		
		and:
		Path extraRequirements = Stub()
		
		and:
		@Subject
		PythonSetupService serviceWithExtraRequirements = 
				new PythonSetupService(
						pythonProperties.toBuilder().extraRequirements(extraRequirements).build(), 
						io, 
						processService, 
						webClient, 
						existingEnvironmentProvider, 
						systemPythonProvider)

		when:
		block serviceWithExtraRequirements.getPythonInterpreter()

		then:
		1 * io.createTempFile("requirements", ".txt") >> Paths.get("/tmp/requirements.txt")
		1 * io.notExists(Paths.get("/path/to/python/requirements.last")) >> true
		1 * io.newOutputStream(Paths.get("/tmp/requirements.txt")) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> 0L
		1 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * bindArg("requirementsTxt", Paths.get("/tmp/requirements.txt")) >> it
				_ >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}

		and:
		1 * io.createTempFile("extra-requirements", ".txt") >> Paths.get("/tmp/extra-requirements.txt")
		1 * io.newInputStream(extraRequirements) >> { new ByteArrayInputStream("extra-requirements".bytes) }
		1 * io.newOutputStream(Paths.get("/tmp/extra-requirements.txt")) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> { l, r -> l.transferTo(r) }
		1 * io.notExists(Paths.get("/path/to/python/extra-requirements.last")) >> true
		1 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * bindArg("requirementsTxt", Paths.get("/tmp/extra-requirements.txt")) >> it
				_ >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}
		1 * io.writeString(Paths.get("/path/to/python/extra-requirements.last"), STUB_EXTRA_REQUIREMENTS_SHA_256_HASH, _)
	}

	void "Installs user requirements into existing environments when they have changed"() {
		given:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(Paths.get("venv/"))

		and:
		Path extraRequirements = Stub()

		and:
		@Subject
		PythonSetupService serviceWithExtraRequirements =
				new PythonSetupService(
						pythonProperties.toBuilder().extraRequirements(extraRequirements).build(),
						io,
						processService,
						webClient, 
						existingEnvironmentProvider, 
						systemPythonProvider)

		when:
		block serviceWithExtraRequirements.getPythonInterpreter()

		then:
		1 * io.createTempFile("extra-requirements", ".txt") >> Paths.get("/tmp/extra-requirements.txt")
		1 * io.newInputStream(extraRequirements) >> { new ByteArrayInputStream("extra-requirements".bytes) }
		1 * io.newOutputStream(Paths.get("/tmp/extra-requirements.txt")) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> { l, r -> l.transferTo(r) }
		1 * io.notExists(Paths.get("/path/to/python/extra-requirements.last")) >> false
		1 * io.readString(Paths.get("/path/to/python/extra-requirements.last")) >> "old-checksum"
		1 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * bindArg("requirementsTxt", Paths.get("/tmp/extra-requirements.txt")) >> it
				_ >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}
		1 * io.writeString(Paths.get("/path/to/python/extra-requirements.last"), STUB_EXTRA_REQUIREMENTS_SHA_256_HASH, _)
	}

	void "Skips installing user requirements into existing environments when they have not changed"() {
		given:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(Paths.get("venv/"))

		and:
		Path extraRequirements = Stub()

		and:
		@Subject
		PythonSetupService serviceWithExtraRequirements =
				new PythonSetupService(
						pythonProperties.toBuilder().extraRequirements(extraRequirements).build(),
						io,
						processService,
						webClient, 
						existingEnvironmentProvider, 
						systemPythonProvider)

		when:
		block serviceWithExtraRequirements.getPythonInterpreter()

		then:
		0 * io.createTempFile("python_requirements", ".txt") >> Paths.get("/tmp/requirements.txt")

		and:
		1 * io.createTempFile("extra-requirements", ".txt") >> Paths.get("/tmp/extra-requirements.txt")
		1 * io.newInputStream(extraRequirements) >> { new ByteArrayInputStream("extra-requirements".bytes) }
		1 * io.newOutputStream(Paths.get("/tmp/extra-requirements.txt")) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> { l, r -> l.transferTo(r) }
		1 * io.notExists(Paths.get("/path/to/python/extra-requirements.last")) >> false
		1 * io.readString(Paths.get("/path/to/python/extra-requirements.last")) >> STUB_EXTRA_REQUIREMENTS_SHA_256_HASH
		0 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _)
		0 * io.writeString(Paths.get("/path/to/python/extra-requirements.last"))
	}

	@RestoreSystemProperties
	void "Returns error if download integrity check failed"() {
		given:
		System.setProperty("os.name", "Windows")

		and:
		io.notExists(pythonProperties.path().resolve("venv/pyvenv.cfg")) >> true
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		systemPythonProvider.systemPython() >> Mono.empty()

		and:
		webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			uri(pythonProperties.downloadUrl()) >> Mock(WebClient.RequestHeadersSpec) {
				retrieve() >> Mock(WebClient.ResponseSpec) {
					bodyToFlux(DataBuffer.class) >> DataBufferUtils.readByteChannel(
							() -> Channels.newChannel(new ByteArrayInputStream([1, 2, 3] as byte[])), DefaultDataBufferFactory.sharedInstance, 8)
				}
			}
		}
		io.newOutputStream(_) >> Stub(OutputStream)
		io.write(_, _) >> { Flux<DataBuffer> buffers, OutputStream os -> DataBufferUtils.write(buffers, os) }

		when:
		block service.getPythonInterpreter()

		then:
		thrown(IllegalStateException)
	}

	void "Re-installs base requirements into existing environments when they have changed"() {
		given:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(Paths.get("venv/"))

		when:
		block service.getPythonInterpreter()

		then:
		1 * io.createTempFile("requirements", ".txt") >> Paths.get("/tmp/requirements.txt")
		1 * io.newOutputStream(Paths.get("/tmp/requirements.txt")) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> { l, r -> l.transferTo(r) }
		1 * io.notExists(Paths.get("/path/to/python/requirements.last")) >> false
		1 * io.readString(Paths.get("/path/to/python/requirements.last")) >> "old-checksum"
		1 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _) >> { __, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * bindArg("requirementsTxt", Paths.get("/tmp/requirements.txt")) >> it
				_ >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", "", Duration.ZERO))
		}
		1 * io.writeString(Paths.get("/path/to/python/requirements.last"), REAL_BASE_REQUIREMENTS_SHA_256_HASH, _)
	}

	void "Skips installing base requirements into existing environments when they have not changed"() {
		given:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(Paths.get("venv/"))

		when:
		block service.getPythonInterpreter()

		then:
		1 * io.createTempFile("requirements", ".txt") >> Paths.get("/tmp/requirements.txt")
		1 * io.newOutputStream(Paths.get("/tmp/requirements.txt")) >> Stub(OutputStream)
		1 * io.transferTo(_, _) >> { l, r -> l.transferTo(r) }
		1 * io.notExists(Paths.get("/path/to/python/requirements.last")) >> false
		1 * io.readString(Paths.get("/path/to/python/requirements.last")) >> REAL_BASE_REQUIREMENTS_SHA_256_HASH
		0 * processService.execute(pythonProperties.path().resolve("venv/").resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), _)
		0 * io.writeString(Paths.get("/path/to/python/requirements.last"))
	}

	void "Purges entire Python environment"() {
		when:
		block service.purgeEnvironment()
		
		then:
		1 * io.deleteDirectoryRecursively(Paths.get("/path/to/python/"))
	}
}
