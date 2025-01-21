package io.camunda.rpa.worker.script.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

@Builder
record EvaluateScriptRequest(@NotBlank String script, Map<String, Object> variables) { }
