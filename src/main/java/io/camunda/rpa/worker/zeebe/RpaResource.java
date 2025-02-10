package io.camunda.rpa.worker.zeebe;

record RpaResource(
		String id,
		String name,
		String executionPlatform,
		String executionPlatformVersion,
		String script) { }
