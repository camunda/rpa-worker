package io.camunda.rpa.worker.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DefaultObjectMapperFactory implements ObjectMapperFactory {
	@Override
	public ObjectMapper get() {
		return JsonMapper.builder()
				.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
				.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
				.build();
	}
}
