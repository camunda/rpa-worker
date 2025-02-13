package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.robot.ExecutionResults;

import java.net.URI;
import java.util.Map;

public record EvaluateScriptResponse(
		ExecutionResults.Result result,
		String log,
		Map<String, Object> variables, 
		Map<String, URI> workspace) { }
