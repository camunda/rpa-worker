package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.PythonInterpreter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.function.UnaryOperator;

@RequiredArgsConstructor
//@Component
class PythonRobotExecutionStrategy implements RobotExecutionStrategy {
	
	private final ProcessService processService;
	private final PythonInterpreter pythonInterpreter;
	
	@Override
	public Mono<ProcessService.ExecutionResult> executeRobot(UnaryOperator<ExecutionCustomizer> customizer) {
		return processService.execute(pythonInterpreter.path(), c -> customizer.apply(c
				.arg("-m").arg("robot")
		));
	}
}
