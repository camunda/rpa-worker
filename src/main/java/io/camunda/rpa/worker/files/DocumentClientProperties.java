package io.camunda.rpa.worker.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("camunda.rpa.zeebe.documents")
record DocumentClientProperties(URI documentsEndpoint) { }
