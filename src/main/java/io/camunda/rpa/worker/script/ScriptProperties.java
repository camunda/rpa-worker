package io.camunda.rpa.worker.script;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties("camunda.rpa.scripts")
record ScriptProperties(Path path) { }
