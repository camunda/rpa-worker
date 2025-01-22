package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ProcessService
import org.apache.commons.exec.CommandLine
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.UnaryOperator
import java.util.stream.Stream

class PythonSetupServiceSpec extends Specification {
	
	PythonProperties pythonProperties = new PythonProperties(Paths.get("/path/to/python/"), "https://python/python".toURI())
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
		run(_) >> { Runnable fn -> Mono.fromRunnable(fn) }
	}
	ProcessService processService = Mock()
	WebClient webClient = Mock()
	
	@Subject
	PythonSetupService service = new PythonSetupService(pythonProperties, io, processService, webClient)

	void setupSpec() {
		CommandLine.metaClass.equals = { CommandLine other ->
			CommandLine thiz = delegate as CommandLine
			return thiz.file == other.file
					&& thiz.arguments == other.arguments
					&& thiz.executable == other.executable
		}
	}

	void "Returns existing environment if available"() {
		when:
		PythonInterpreter r = service.getObject()
		
		then:
		1 * io.notExists(pythonProperties.path().resolve("pyvenv.cfg")) >> false
		
		and:
		r.path() == pythonProperties.path().resolve("bin/python")
		
		and:
		0 * io._(*_)
	}
	
	void "Creates new environment using system Python"() {
		when:
		PythonInterpreter r = service.getObject()

		then:
		1 * io.notExists(pythonProperties.path().resolve("pyvenv.cfg")) >> true
		1 * processService.execute("python", _) >> { __, UnaryOperator<ProcessService.ExecutionCustomizer> fn ->
			fn.apply(Mock(ProcessService.ExecutionCustomizer) {
				1 * arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.12.8", ""))
		}
		
		and:
		1 * processService.execute("python", _) >> { __, UnaryOperator<ProcessService.ExecutionCustomizer> fn ->
			fn.apply(Mock(ProcessService.ExecutionCustomizer) {
				1 * arg("-m") >> it
				1 * arg("venv") >> it
				1 * bindArg("pyEnvPath", pythonProperties.path()) >> it
				1 * inheritEnv() >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", ""))
		}
		
		and:
		1 * io.createTempFile("python_requirements", ".txt") >> Paths.get("/tmp/requirements.txt")
		1 * io.copy(_, Paths.get("/tmp/requirements.txt"), _)
		1 * processService.execute(pythonProperties.path().resolve("bin/pip"), _) >> { __, UnaryOperator<ProcessService.ExecutionCustomizer> fn ->
			fn.apply(Mock(ProcessService.ExecutionCustomizer) {
				1 * arg("install") >> it
				1 * arg("-r") >> it
				1 * bindArg("requirementsTxt", Paths.get("/tmp/requirements.txt")) >> it
				1 * inheritEnv() >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "", ""))
		}

		and:
		r.path() == pythonProperties.path().resolve("bin/python")
	}
	
	@RestoreSystemProperties
	void "Downloads Python if no system Python available"() {
		given:
		System.setProperty("os.name", "Windows")
		
		and:
		Path pythonExtractDir = Paths.get("/tmp/python/")
		Path pythonArchive = Paths.get("/tmp/python.zip")
		Path pythonArchiveRootDir = Paths.get("/")
		FileSystem pythonArchiveFs = Stub() {
			getPath("/") >> pythonArchiveRootDir
		}
		
		when:
		PythonInterpreter r = service.getObject()

		then: "Existing Python venv is checked (not there) and system Python is checked (not there)"
		1 * io.notExists(pythonProperties.path().resolve("pyvenv.cfg")) >> true
		1 * processService.execute("python", _) >> { __, UnaryOperator<ProcessService.ExecutionCustomizer> fn ->
			return Mono.error(new IOException("No Python here"))
		}
		
		and: "The standalone Python archive is downloaded from the configured URL"
		1 * io.createTempDirectory(_) >> pythonExtractDir
		1 * io.createTempFile(_, ".zip") >> pythonArchive
		
		1 * webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			1 * uri(pythonProperties.downloadUrl()) >> Mock(WebClient.RequestHeadersSpec) {
				1 * retrieve() >> Mock(WebClient.ResponseSpec) {
					1 * bodyToFlux(DataBuffer.class) >> Flux.empty()
				}
			}
		}
		
		1 * io.write({ it }, pythonArchive) >> Mono.empty()
		
		and: "The downloaded Python archive is extracted"
		1 * io.doWithFileSystem(_, _) >> { __, Consumer<FileSystem> fn -> fn.accept(pythonArchiveFs) }
		1 * io.walk(pythonArchiveRootDir) >> Stream.of(
				Paths.get("/aDir/anotherDir/aFile"),
				Paths.get("/aDir/differentDir/anotherFile"))
		io.isRegularFile(_, _) >> true

		1 * io.createDirectories(pythonExtractDir.resolve("aDir/anotherDir/"))
		1 * io.copy(_, pythonExtractDir.resolve("aDir/anotherDir/aFile"))
		1 * io.createDirectories(pythonExtractDir.resolve("aDir/differentDir/"))
		1 * io.copy(_, pythonExtractDir.resolve("aDir/differentDir/anotherFile"))
		
		1 * io.list(pythonExtractDir) >> Stream.of(Paths.get("aDir/"))
		
		and:
		r.path() == pythonProperties.path().resolve("bin/python")

		then:
		processService.execute(_, _) >> Mono.empty()
	}
}
