package io.camunda.rpa.worker.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
public class YamlMapper {
	@Delegate
	private final ObjectMapper objectMapper;
}
