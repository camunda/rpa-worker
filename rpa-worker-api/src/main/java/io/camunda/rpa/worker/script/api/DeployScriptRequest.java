package io.camunda.rpa.worker.script.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record DeployScriptRequest(@NotBlank String id, @NotBlank String script) { }
