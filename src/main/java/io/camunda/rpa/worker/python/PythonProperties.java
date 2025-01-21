package io.camunda.rpa.worker.python;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;

@ConfigurationProperties("camunda.rpa.python")
record PythonProperties(Path path, URI downloadUrl) {
}
