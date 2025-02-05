package io.camunda.rpa.worker.zeebe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.client.auth")
record ZeebeAuthProperties(String clientId, String clientSecret) { }
