package io.camunda.rpa.worker.secrets.k8s;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("camunda.rpa.secrets.k8s")
record K8sProperties(List<String> secrets) { }
