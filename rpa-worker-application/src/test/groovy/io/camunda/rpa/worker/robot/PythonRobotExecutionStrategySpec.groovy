package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.PythonInterpreter
import org.springframework.beans.factory.ObjectProvider
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths
import java.util.function.UnaryOperator

class PythonRobotExecutionStrategySpec extends Specification {
	
	ProcessService processService = Mock()
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))
	ObjectProvider<PythonInterpreter> pythonInterpreterProvider = Stub() {
		getObject() >> { pythonInterpreter }
	}
	
	@Subject
	PythonRobotExecutionStrategy strategy = new PythonRobotExecutionStrategy(processService, pythonInterpreterProvider)
	
	void "Configures execution and executes with Python interpreter"() {
		given:
		ExecutionCustomizer ec = Mock(ExecutionCustomizer) {
			1 * arg("-m") >> it
			1 * arg("robot") >> it
		}
		
		UnaryOperator<ExecutionCustomizer> executionCustomizer = Mock() {
			1 * apply(ec) >> ec
		}
		
		and:
		Mono<ProcessService.ExecutionResult> result = Stub()

		when:
		Mono<ProcessService.ExecutionResult> r = strategy.executeRobot(executionCustomizer)
		
		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(ec)
			return result
		}
		
		and:
		r == result
	}
}
