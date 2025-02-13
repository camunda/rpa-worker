package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.PythonInterpreter
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths
import java.util.function.UnaryOperator

class RobotStartupCheckSpec extends Specification implements PublisherUtils {
	
	ProcessService processService = Mock()
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))
	
	@Subject
	RobotStartupCheck check = new RobotStartupCheck(processService, pythonInterpreter)

	ExecutionCustomizer executionCustomizer = Mock() {
		_ >> it
	}

	void "Returns ready event on successful check"() {
		when:
		RobotReadyEvent event = block check.check()
		
		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			c.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_HELP_OR_VERSION_REQUEST, "Robot Framework 7.2", ""))
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
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_INTERNAL_ERROR, "", "Robot is poorly"))
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
