package io.camunda.rpa.worker.script.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import org.intellij.lang.annotations.Language;

import java.util.Map;

@Builder
public record EvaluateScriptRequest(@NotBlank @Language("Robot") String script, Map<String, Object> variables, String workspaceAffinityKey) { }
