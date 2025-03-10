package io.camunda.rpa.worker.secrets.camunda;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("camunda.rpa.secrets.camunda")
record CamundaSecretsClientProperties(URI secretsEndpoint, String tokenAudience) { }
