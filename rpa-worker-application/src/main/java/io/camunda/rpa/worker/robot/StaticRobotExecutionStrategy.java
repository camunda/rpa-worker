package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Paths;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
@Component
class StaticRobotExecutionStrategy implements RobotExecutionStrategy {
	
	private final ProcessService processService;
	
	@Override
	public Mono<ProcessService.ExecutionResult> executeRobot(UnaryOperator<ExecutionCustomizer> customizer) {
		return processService.execute(
				Paths.get("/home/pounder/workspace/camunda/standalone-robotframework-test/dist/robot_runner"), 
				customizer);
	}
}
