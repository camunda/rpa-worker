package io.camunda.rpa.worker.script.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.rpa.sandbox")
record ScriptSandboxProperties(boolean enabled) { }
