package io.camunda.rpa.worker.robot;

import lombok.Getter;

@Getter
public class RobotFailureException extends RuntimeException {
	
	private final ExecutionResults.ExecutionResult executionResult;
	
	public RobotFailureException(ExecutionResults.ExecutionResult executionResult) {
		super("Robot results were not successful");
		this.executionResult = executionResult;
	}
}
