package io.camunda.rpa.worker.files;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

public record ZeebeDocumentDescriptor(
		String storeId, 
		@NotBlank String documentId, 
		Metadata metadata, 
		String contentHash) {
	
	public record Metadata(
			String contentType,
			String fileName,
			@Future Instant expiresAt,
			@Positive Long size,
			String processDefinitionId,
			@Positive Long processInstanceKey, 
			Map<String, String> customProperties) { }
}
