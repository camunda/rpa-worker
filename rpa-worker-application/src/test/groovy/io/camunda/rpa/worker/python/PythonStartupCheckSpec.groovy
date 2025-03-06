package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths
import java.time.Duration
import java.util.function.UnaryOperator

class PythonStartupCheckSpec extends Specification implements PublisherUtils {
	
	ProcessService processService = Mock()
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))
	
	@Subject
	PythonStartupCheck check = new PythonStartupCheck(processService, pythonInterpreter)

	ExecutionCustomizer executionCustomizer = Mock() {
		_ >> it
	}

	void "Returns ready event on successful check"() {
		when:
		PythonReadyEvent event = block check.check()
		
		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			c.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.12.8", "", Duration.ZERO))
		}
		
		and:
		1 * executionCustomizer.noFail() >> executionCustomizer
		
		and:
		event
	}

	void "Returns error when check is unsuccessful (post-invoke)"() {
		when:
		block check.check()

		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			c.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(255, "", "Python is poorly", Duration.ZERO))
		}
		
		and:
		thrown(Exception)
	}

	void "Returns error when check is unsuccessful (pre-invoke)"() {
		when:
		block check.check()

		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			return Mono.error(new IOException("No Python"))
		}

		and:
		thrown(Exception)
	}
}
