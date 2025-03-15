package io.camunda.rpa.worker.zeebe.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

@Builder
record JobThrowErrorRequest(
		@NotBlank String errorCode,
		String errorMessage,
		Map<String, Object> variables) { }
