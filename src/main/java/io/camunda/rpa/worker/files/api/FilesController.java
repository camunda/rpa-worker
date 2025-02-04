package io.camunda.rpa.worker.files.api;

import io.camunda.rpa.worker.files.DocumentClient;
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.workspace.WorkspaceFile;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
class FilesController {
	
	static final String ZEEBE_TOKEN_AUDIENCE = "zeebe.camunda.io";
	
	private final WorkspaceService workspaceService;
	private final IO io;
	private final ZeebeAuthenticationService zeebeAuthenticationService;
	private final DocumentClient documentClient;
	
	@PostMapping("/store/{workspaceId}")
	public Mono<Map<String, ZeebeDocumentDescriptor>> storeFiles(
			@PathVariable String workspaceId, 
			@RequestBody StoreFilesRequest request) {

		PathMatcher pathMatcher = io.globMatcher(request.files());
		
		return io.supply(() -> workspaceService.getById(workspaceId)
						.stream()
						.flatMap(io::walk)
						.filter(pathMatcher::matches))
				.flatMapMany(Flux::fromStream)
				.flatMap(p -> Mono.justOrEmpty(workspaceService.getWorkspaceFile(workspaceId, p.toString())))
				.flatMap(p -> zeebeAuthenticationService.getAuthToken(ZEEBE_TOKEN_AUDIENCE)
						.flatMap(token -> documentClient.uploadDocument(token, toZeebeStoreDocumentRequest(p), null)))
				.collect(Collectors.toMap(
						r -> r.metadata().fileName(),
						r -> new ZeebeDocumentDescriptor(r.storeId(), r.documentId(), r.metadata())));
	}

	private MultiValueMap<String, HttpEntity<?>> toZeebeStoreDocumentRequest(WorkspaceFile file) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();

		builder.part("metadata", new ZeebeDocumentDescriptor.Metadata(
						file.contentType(),
						file.path().getFileName().toString(),
						null,
						file.size()))
				.contentType(MediaType.APPLICATION_JSON);

		builder.asyncPart("file",
						DataBufferUtils.read(file.path(), DefaultDataBufferFactory.sharedInstance, 8192),
						DataBuffer.class)
				.contentType(MediaType.parseMediaType(file.contentType()))
				.filename(file.path().getFileName().toString());

		return builder.build();
	}
}
