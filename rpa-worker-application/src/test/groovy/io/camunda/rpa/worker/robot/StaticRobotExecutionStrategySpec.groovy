package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.PythonInterpreter
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths
import java.util.function.UnaryOperator

class StaticRobotExecutionStrategySpec extends Specification {
	
	ProcessService processService = Mock()
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))
	IO io = Stub() {
		run(_) >> Mono.empty()
	}
	
	@Subject
	StaticRobotExecutionStrategy strategy = new StaticRobotExecutionStrategy(processService, io)
	
	void "Configures execution and executes with static runtime"() {
		given:
		ExecutionCustomizer ec = Stub(ExecutionCustomizer)
		
		and:
		Mono<ProcessService.ExecutionResult> result = Stub()

		when:
		Mono<ProcessService.ExecutionResult> r = strategy.executeRobot(c -> c)
		
		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { _, UnaryOperator<ExecutionCustomizer> fn ->
			fn.apply(ec)
			return result
		}
		
		and:
		r == result
	}
}
