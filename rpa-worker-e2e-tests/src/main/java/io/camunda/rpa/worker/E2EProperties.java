package io.camunda.rpa.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;

@ConfigurationProperties("camunda.rpa.e2e")
public record E2EProperties(
		Path pathToWorker,
		String camundaHost,
		String clientId,
		String clientSecret,
		boolean noStartWorker,
		URI operateUrl,
		String operateClient,
		String operateClientSecret,
		String operateTokenAudience) {
}
