package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.python.PythonRuntimeProperties;

@FunctionalInterface
public interface ResolvedRobotExecutionStrategyType {
	PythonRuntimeProperties.PythonRuntimeEnvironment getType();
}
