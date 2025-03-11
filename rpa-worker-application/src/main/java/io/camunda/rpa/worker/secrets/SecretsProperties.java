package io.camunda.rpa.worker.secrets;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.rpa.secrets")
record SecretsProperties(String backend) { }
