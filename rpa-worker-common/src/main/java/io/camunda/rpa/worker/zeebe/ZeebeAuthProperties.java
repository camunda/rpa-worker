package io.camunda.rpa.worker.zeebe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.client.auth")
public record ZeebeAuthProperties(String clientId, String clientSecret) { }
