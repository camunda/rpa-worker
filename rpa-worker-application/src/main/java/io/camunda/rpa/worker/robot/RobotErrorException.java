package io.camunda.rpa.worker.robot;

public class RobotErrorException extends RuntimeException {
	public RobotErrorException(Throwable cause) {
		super("Failed to invoke Robot", cause);
	}
}
