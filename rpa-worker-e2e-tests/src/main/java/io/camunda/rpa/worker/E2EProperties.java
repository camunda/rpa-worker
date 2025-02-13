package io.camunda.rpa.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("camunda.rpa.e2e")
public record E2EProperties(Path pathToWorker) {
}
