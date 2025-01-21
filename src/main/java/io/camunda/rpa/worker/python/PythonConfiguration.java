package io.camunda.rpa.worker.python;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PythonConfiguration {
	
	@Bean
	public PythonInterpreter pythonInterpreter(PythonProperties properties) {
		return new PythonInterpreter(properties.path().resolve("bin/python"));
	}
	
}
