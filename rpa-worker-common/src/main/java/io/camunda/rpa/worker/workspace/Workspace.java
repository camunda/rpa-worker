package io.camunda.rpa.worker.workspace;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public record Workspace(String id, Path path, Map<String, Object> properties, String affinityKey) {
	
	public Workspace(String workspaceId, Path path) {
		this(workspaceId, path, Collections.emptyMap(), null);
	}

	public Workspace(String workspaceId, Path path, String affinityKey) {
		this(workspaceId, path, Collections.emptyMap(), affinityKey);
	}

	@SuppressWarnings("unchecked")
	public <T> T getProperty(String name) {
		return (T) properties.get(name);
	}
}
