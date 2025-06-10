package io.camunda.rpa.worker.script.api;

import java.util.Map;

public interface EvaluateScriptRequest {
	Map<String, Object> variables();
	String workspaceAffinityKey();
}