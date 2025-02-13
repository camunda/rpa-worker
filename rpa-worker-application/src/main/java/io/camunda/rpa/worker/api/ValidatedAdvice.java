package io.camunda.rpa.worker.api;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
class ValidatedAdvice {
	
	@ExceptionHandler
	public Mono<ResponseEntity<?>> handleValidationFailure(WebExchangeBindException thrown) {
		return Mono.just(ResponseEntity.unprocessableEntity()
				.body(Map.of("fieldErrors", thrown.getFieldErrors().stream()
						.collect(Collectors.toMap(FieldError::getField, err -> new HashMap<>() {{
							put("code", err.getCode());
							put("message", err.getDefaultMessage());
							put("rejectedValue", err.getRejectedValue());
						}})))));
	}
	
}
