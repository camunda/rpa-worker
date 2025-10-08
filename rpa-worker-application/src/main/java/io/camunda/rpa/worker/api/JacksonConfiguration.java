package io.camunda.rpa.worker.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfiguration {

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer objectMapperCustomizer() {
		return b -> b.serializationInclusion(JsonInclude.Include.NON_NULL);
	}
}
