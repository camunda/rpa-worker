package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.workspace.Workspace;
import lombok.Builder;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Builder(toBuilder = true)
public record ExecutionResults(
		Map<String, ExecutionResult> results,
		Result result,
		Map<String, Object> outputVariables,
		Workspace workspace,
		Duration duration) { 
	
	public enum Result {
		PASS, FAIL, ERROR
	}
	
	public record ExecutionResult(
			String executionId,
			Result result,
			String output,
			Map<String, Object> outputVariables,
			Duration duration) {}
	
	public String fullLogString() {
		return results.entrySet().stream()
				.flatMap(kv -> kv.getValue().output().lines()
						.map(l -> "[%s] %s".formatted(kv.getKey(), l)))
				.collect(Collectors.joining("\n"));
	}
}
