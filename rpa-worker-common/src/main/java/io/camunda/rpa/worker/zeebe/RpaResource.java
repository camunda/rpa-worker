package io.camunda.rpa.worker.zeebe;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Singular;

import java.util.Map;

@Builder
public record RpaResource(
		String id,
		String name,
		String executionPlatform,
		String executionPlatformVersion,
		@NotNull String script, 
		@Singular Map<String, String> files) { }
