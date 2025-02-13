package io.camunda.rpa.worker.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.rpa.zeebe.documents")
record DocumentClientProperties() { }
