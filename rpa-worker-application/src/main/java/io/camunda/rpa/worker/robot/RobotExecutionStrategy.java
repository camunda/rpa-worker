package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import reactor.core.publisher.Mono;

import java.util.function.UnaryOperator;

@FunctionalInterface
interface RobotExecutionStrategy {
	Mono<ProcessService.ExecutionResult> executeRobot(UnaryOperator<ExecutionCustomizer> customizer);
}
