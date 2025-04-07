package io.camunda.rpa.worker.api;

import lombok.Builder;

@Builder
record StubbedResponse(
		String target,
		String action,
		Object request) { }
