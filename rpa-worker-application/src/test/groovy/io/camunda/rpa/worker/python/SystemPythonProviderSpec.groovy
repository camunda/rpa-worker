package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.function.UnaryOperator

class SystemPythonProviderSpec extends Specification implements PublisherUtils {
	
	PythonProperties pythonProperties = PythonProperties.builder().build()
	ProcessService processService = Mock()
	
	@Subject
	SystemPythonProvider provider = new SystemPythonProvider(pythonProperties, processService)
	
	void "Returns system Python when valid (exe name is python3)"() {
		given:
		processService.execute("python", _) >> Mono.error(new IOException())

		when:
		Object r = block provider.systemPython()
		
		then:
		1 * processService.execute("python3", _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * silent() >> it
				1 * arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.11.1", "", null))
		}
		
		and:
		r == "python3"
	}

	void "Returns system Python when valid (exe name is python)"() {
		given:
		processService.execute("python3", _) >> Mono.error(new IOException())

		when:
		Object r = block provider.systemPython()

		then:
		1 * processService.execute("python", _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * silent() >> it
				1 * arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.11.1", "", null))
		}

		and:
		r == "python"
	}
	
	void "Uses configured Python interpreter when configured"() {
		Path pythonInterpreter = Paths.get("/path/to/custom/interpreter")
		given:
		@Subject
		SystemPythonProvider newProvider = new SystemPythonProvider(pythonProperties
				.toBuilder()
				.interpreter(pythonInterpreter).build(),
				processService)

		when:
		Object r = block newProvider.systemPython()

		then:
		1 * processService.execute(pythonInterpreter, _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * silent() >> it
				1 * arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.11.1", "", null))
		}

		and:
		r == pythonInterpreter
	}

	void "Fake Windows Python is ignored"() {
		given: "The Python on the path is some fake Windows store launcher thing"
		processService.execute(_, _) >> Mono.just(
				new ProcessService.ExecutionResult(SystemPythonProvider.WINDOWS_NO_PYTHON_EXIT_CODES.first(), "", "", Duration.ZERO))

		when:
		Object r = block provider.systemPython()

		then: "Fake Python is ignored and returns empty"
		! r
	}

	void "Returns empty when system Python is not valid - Too Old"() {
		given:
		processService.execute("python", _) >> Mono.error(new IOException())

		when:
		Object r = block provider.systemPython()

		then:
		1 * processService.execute("python3", _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * silent() >> it
				1 * arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.6.0", "", null))
		}

		and:
		! r
	}

	void "Returns empty when system Python is not valid - Too New"() {
		given:
		processService.execute("python", _) >> Mono.error(new IOException())

		when:
		Object r = block provider.systemPython()

		then:
		1 * processService.execute("python3", _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(Mock(ExecutionCustomizer) {
				1 * silent() >> it
				1 * arg("--version") >> it
			})
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.13.0", "", null))
		}

		and:
		! r
	}
}
