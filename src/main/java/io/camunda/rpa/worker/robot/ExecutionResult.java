package io.camunda.rpa.worker.robot;

import java.util.Map;

public record ExecutionResult(
		Result result,
		String output, 
		Map<String, Object> outputVariables) { 
	
	public enum Result {
		PASS, FAIL, ERROR
	}
}
