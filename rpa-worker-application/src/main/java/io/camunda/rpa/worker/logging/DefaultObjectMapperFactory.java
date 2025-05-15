package io.camunda.rpa.worker.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

class DefaultObjectMapperFactory implements ObjectMapperFactory {
	@Override
	public ObjectMapper get() {
		return new ObjectMapper()
				.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}
}
