package io.camunda.rpa.worker.script;

import java.nio.file.Path;

@Deprecated(forRemoval = true)
public record ScriptMetadata(String id, Path path) { }
