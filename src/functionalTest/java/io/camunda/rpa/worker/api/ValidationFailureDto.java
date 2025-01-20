package io.camunda.rpa.worker.api;

import java.util.Map;

public record ValidationFailureDto(Map<String, FieldError> fieldErrors) {
	public record FieldError(String code, String message, Object rejectedValue) {}
}
