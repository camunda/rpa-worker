package io.camunda.rpa.worker.robot;

import java.nio.file.Path;

@FunctionalInterface
public interface RobotExecutionListener {
	void afterRobotExecution(Path workspace);
}
