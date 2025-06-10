package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.zeebe.RpaResource;
import jakarta.validation.Valid;
import lombok.Builder;

import java.util.Map;

@Builder
public record EvaluateRichScriptRequest(
		@Valid RpaResource rpa,
		Map<String, Object> variables,
		String workspaceAffinityKey) implements EvaluateScriptRequest { }
