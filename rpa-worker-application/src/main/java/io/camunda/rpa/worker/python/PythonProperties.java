package io.camunda.rpa.worker.python;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;

@ConfigurationProperties("camunda.rpa.python")
@Builder(toBuilder = true)
record PythonProperties(
		Path path, 
		URI downloadUrl, 
		String downloadHash, 
		String requirementsName, 
		Path extraRequirements,
		String newEnvironmentPipLine) {
}
