package io.camunda.rpa.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("camunda.rpa.e2e")
public record E2EProperties(
		Path pathToWorker,
		String camundaHost,
		String clientSecret,
		boolean noStartWorker,
		String operateClient,
		String operateClientSecret) {
}
