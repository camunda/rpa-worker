package io.camunda.rpa.worker.pexec

import io.camunda.rpa.worker.PublisherUtils
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import reactor.core.scheduler.Schedulers
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

class ProcessServiceSpec extends Specification implements PublisherUtils {
	
	DefaultExecutor defaultExecutor = Mock()
	DefaultExecutor.Builder executorBuilder = Mock() {
		get() >> defaultExecutor
	}
	
	@Subject
	ProcessService service = new ProcessService(Schedulers.single(), { executorBuilder })
	
	void setupSpec() {
		CommandLine.metaClass.equals = { CommandLine other ->
			CommandLine thiz = delegate as CommandLine
			return thiz.file == other.file
					&& thiz.arguments == other.arguments
					&& thiz.executable == other.executable
		}
	}
	
	void "Executes processes as configured"() {
		given:
		Path someExe = Paths.get("/path/to/exe")
		
		Path someArgValue = Paths.get("/some/arg/value")

		File workDirAsFile = Stub(File)
		Path workDir = Stub() {
			toFile() >> workDirAsFile
		}
		
		and:
		CommandLine expectedCommandLine = new CommandLine(someExe.toFile())
			.addArgument("normalArg")
			.addArgument('${pathBoundArg}')
			.addArgument('${otherBoundArg}').tap {
			setSubstitutionMap([pathBoundArg: someArgValue.toFile(), otherBoundArg: '123'])
		}
		
		Map<String, Object> expectedEnvironment = [ENV_VAR: 'env-var-value']
		ProcessService.ExecutionResult expectedResult = new ProcessService.ExecutionResult(1, "stdout-content", "stderr-content")
		
		and:
		ProcessService.StreamHandler streamHandler
		defaultExecutor.setStreamHandler(_) >> { ProcessService.StreamHandler sh -> streamHandler = sh }
		
		when:
		ProcessService.ExecutionResult result = block service.execute(someExe, c -> c
				.allowExitCode(1)
				.arg("normalArg")
				.bindArg("pathBoundArg", someArgValue)
				.bindArg("otherBoundArg", 123)
				.workDir(workDir)
				.env("ENV_VAR", "env-var-value"))
		
		then:
		1 * executorBuilder.setWorkingDirectory(workDirAsFile)
		1 * defaultExecutor.execute(expectedCommandLine, expectedEnvironment) >> {
			streamHandler.out << "stdout-content"
			streamHandler.err << "stderr-content"
			return 1
		}
		
		and:
		result == expectedResult
	}
	
	void "Executable can be a simple String"() {
		given:
		String someExe = "someExe"

		and:
		CommandLine expectedCommandLine = new CommandLine(someExe)

		when:
		block service.execute(someExe, c -> c)

		then:
		1 * defaultExecutor.execute(expectedCommandLine, _)
	}
	
	void "Merges environment variables from all sources"() {
		when:
		block service.execute("someExe", c -> c
				.env("key1", "val1")
				.env([key2: "val2"])
				.inheritEnv())

		then:
		1 * defaultExecutor.execute(_, [
				key1: "val1", 
				key2: "val2", 
				*: System.getenv()]
		)
	}
	
	void "Returns error publisher when process execution fails"() {
		given:
		defaultExecutor.execute(_, _) >> { throw new RuntimeException("Bang!") }
		
		when:
		block service.execute("someExe", c -> c)

		then:
		thrown(Exception)
	}

	void "Return correct ExecutionResult for process failure"() {
		given:
		defaultExecutor.execute(_, _) >> { throw new ExecuteException("Bang!", 127) }

		when:
		ProcessService.ExecutionResult result = block service.execute("someExe", c -> c)

		then:
		result.exitCode() == 127
	}

	
	@Timeout(3)
	void "Applies timeout, aborting execution and throwing when exceeded"() {
		given:
		Path someExe = Paths.get("/path/to/exe")
		
		and:
		Process process = Mock()
		ExecuteWatchdog2 watchdog
		defaultExecutor.setWatchdog(_) >> { ExecuteWatchdog2 w -> 
			w.start(process)
			watchdog = w 
		}
		defaultExecutor.execute(_, _) >> {
			Thread.sleep(3_500)
			return 1
		}

		when:
		block service.execute(someExe, c -> c
				.timeout(Duration.ofSeconds(1)))

		then:
		1 * process.toHandle() >> Mock(ProcessHandle) {
			1 * destroy() >> true
		}
		
		and:
		thrown(ProcessTimeoutException)
	}

}
