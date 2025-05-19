package io.camunda.rpa.worker.zeebe;

import lombok.Builder;
import lombok.Singular;

import java.util.Map;

@Builder
record RpaResource(
		String id,
		String name,
		String executionPlatform,
		String executionPlatformVersion,
		String script, 
		@Singular Map<String, String> files) { }
