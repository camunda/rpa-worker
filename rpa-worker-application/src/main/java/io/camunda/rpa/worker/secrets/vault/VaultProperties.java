package io.camunda.rpa.worker.secrets.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("camunda.rpa.secrets.vault")
record VaultProperties(List<String> secrets) { }
