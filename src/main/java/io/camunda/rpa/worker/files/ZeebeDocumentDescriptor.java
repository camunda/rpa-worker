package io.camunda.rpa.worker.files;

import java.time.Instant;

public record ZeebeDocumentDescriptor(String storeId, String documentId, Metadata metadata) {
	
	public record Metadata(
			String contentType,
			String fileName,
			Instant expiresAt,
			long size) { }
}
