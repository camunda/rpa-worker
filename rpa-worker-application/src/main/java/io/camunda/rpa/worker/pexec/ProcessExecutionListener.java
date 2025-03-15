package io.camunda.rpa.worker.pexec;

public interface ProcessExecutionListener {
	void processStarted(ProcessControl processControl);
	void processEnded(ProcessService.ExecutionResult executionResult);
}
