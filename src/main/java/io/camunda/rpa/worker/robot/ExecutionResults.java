package io.camunda.rpa.worker.robot;

import java.util.Map;

public record ExecutionResults(
		Map<String, ExecutionResult> results,
		Map<String, Object> outputVariables) { 
	
	public enum Result {
		PASS, FAIL, ERROR
	}
	
	public record ExecutionResult(String executionId, Result result, String output) {}
}
