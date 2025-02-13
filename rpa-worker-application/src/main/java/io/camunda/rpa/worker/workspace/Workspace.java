package io.camunda.rpa.worker.workspace;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public record Workspace(String id, Path path, Map<String, Object> properties) {
	
	public Workspace(String workspaceId, Path path) {
		this(workspaceId, path, Collections.emptyMap());
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getProperty(String name) {
		return (T) properties.get(name);
	}
}
