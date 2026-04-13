package io.camunda.rpa.worker.logging;

import java.util.List;
import java.util.Map;

record LoggingEvent(
		String timestamp,
		String level,
		String message,
		String logger,
		String thread,
		Thrown thrown,
		Map<String, String> context,
		List<String> tags
) {
	record Thrown(String type, String message, String stacktrace) {}
}
