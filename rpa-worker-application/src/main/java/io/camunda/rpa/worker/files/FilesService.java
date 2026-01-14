package io.camunda.rpa.worker.files;

import io.camunda.rpa.worker.workspace.WorkspaceFile;
import io.camunda.rpa.worker.zeebe.ZeebeJobInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static io.camunda.rpa.worker.util.PathUtils.fixSlashes;

@Service
@RequiredArgsConstructor
public class FilesService {

	private final ObjectProvider<DocumentClient> documentClient;

	public Mono<ZeebeDocumentDescriptor> uploadDocument(WorkspaceFile workspaceFile, ZeebeDocumentDescriptor.Metadata metadata) {
		return documentClient.getObject().uploadDocument(toZeebeStoreDocumentRequest(workspaceFile, metadata), null);
	}
	
	public Flux<DataBuffer> getDocument(String documentId, String storeId, String contentHash) {
		return documentClient.getObject().getDocument(documentId, storeId, contentHash);
	}

	public static ZeebeDocumentDescriptor.Metadata toMetadata(WorkspaceFile file, ZeebeJobInfo zeebeJobInfo) {
		return new ZeebeDocumentDescriptor.Metadata(
				file.contentType(),
				fixSlashes(file.path().getFileName()),
				null,
				file.size(),
				zeebeJobInfo.procesDefinitionId(),
				zeebeJobInfo.processInstanceKey(),
				Collections.emptyMap());
	}

	private MultiValueMap<String, HttpEntity<?>> toZeebeStoreDocumentRequest(
			WorkspaceFile file,
			ZeebeDocumentDescriptor.Metadata metadata) {

		MultipartBodyBuilder builder = new MultipartBodyBuilder();

		builder.part("metadata", metadata)
				.contentType(MediaType.APPLICATION_JSON);

		builder.part("metadata88", metadata.for88())
				.contentType(MediaType.APPLICATION_JSON);

		builder.asyncPart("file",
						DataBufferUtils.read(file.path(), DefaultDataBufferFactory.sharedInstance, 8192),
						DataBuffer.class)
				.contentType(MediaType.parseMediaType(file.contentType()))
				.filename(fixSlashes(file.workspace().path().relativize(file.path())));

		return builder.build();
	}
}
