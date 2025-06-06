package io.camunda.rpa.worker.python;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Builder(toBuilder = true)
@ConfigurationProperties("camunda.rpa.python-runtime")
public record PythonRuntimeProperties(
		PythonRuntimeEnvironment type,
		boolean exitAfterDetect) {
	
	public enum PythonRuntimeEnvironment {
		Auto, Python, Static
	}
	
}
