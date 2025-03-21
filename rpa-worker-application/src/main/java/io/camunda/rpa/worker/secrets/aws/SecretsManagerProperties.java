package io.camunda.rpa.worker.secrets.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("camunda.rpa.secrets.aws")
record SecretsManagerProperties(List<String> secrets) { }
