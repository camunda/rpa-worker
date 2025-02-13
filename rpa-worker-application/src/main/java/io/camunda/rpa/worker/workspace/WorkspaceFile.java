package io.camunda.rpa.worker.workspace;

import org.springframework.http.MediaType;

import java.nio.file.Path;

public record WorkspaceFile(Workspace workspace, String contentType, long size, Path path) {
	public WorkspaceFile {
		if(contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
	}
}
