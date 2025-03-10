package io.camunda.rpa.worker.secrets;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("camunda.rpa.secrets")
public record SecretsProperties(String backend, List<String> secrets) {
}
