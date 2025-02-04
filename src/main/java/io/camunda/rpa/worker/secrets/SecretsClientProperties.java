package io.camunda.rpa.worker.secrets;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("camunda.rpa.zeebe.secrets")
record SecretsClientProperties(URI secretsEndpoint) { }
