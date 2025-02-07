package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.workspace.Workspace;

@FunctionalInterface
public interface RobotExecutionListener {
	void afterRobotExecution(Workspace workspace);
}
