package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.python.PythonRuntimeProperties;

@FunctionalInterface
interface ResolvedRobotExecutionStrategyType {
	PythonRuntimeProperties.PythonRuntimeEnvironment getType();
}
