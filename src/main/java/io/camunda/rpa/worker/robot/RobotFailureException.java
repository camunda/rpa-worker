package io.camunda.rpa.worker.robot;

public class RobotFailureException extends RuntimeException {
	public RobotFailureException(Throwable cause) {
		super("Failed to invoke Robot", cause);
	}
}
