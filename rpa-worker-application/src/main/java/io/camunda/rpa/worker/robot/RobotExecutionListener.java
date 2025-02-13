package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.workspace.Workspace;

import java.time.Duration;

@FunctionalInterface
public interface RobotExecutionListener {
	default void beforeScriptExecution(Workspace workspace, Duration timeout) {}
	
	void afterRobotExecution(Workspace workspace);
}
