package io.camunda.rpa.worker.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfiguration {

	@Bean
	public JsonMapperBuilderCustomizer objectMapperCustomizer() {
		return b -> b
				.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
				.changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
				.build();
	}
}
