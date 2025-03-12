package io.camunda.rpa.worker.files.api;

import feign.FeignException;
import io.camunda.rpa.worker.files.DocumentClient;
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.rpa.worker.workspace.WorkspaceFile;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import jakarta.validation.Valid;
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

import java.net.URI;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.camunda.rpa.worker.util.PathUtils.fixSlashes;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
class FilesController {

	private final WorkspaceService workspaceService;
	private final IO io;
	private final DocumentClient documentClient;

	@PostMapping("/store/{workspaceId}")
	public Mono<Map<String, ZeebeDocumentDescriptor>> storeFiles(
			@PathVariable String workspaceId,
			@Valid @RequestBody StoreFilesRequest request) {

		PathMatcher pathMatcher = io.globMatcher(URI.create(fixSlashes(request.files())).normalize().toString());
		
		return io.wrap(Mono.defer(() -> {
		Optional<Workspace> workspace = workspaceService.getById(workspaceId);

		Stream<WorkspaceFile> filesToStore = workspace
					.stream()
					.map(Workspace::path)
					.flatMap(io::walk)
					.map(p -> workspace.get().path().relativize(p))
					.filter(pathMatcher::matches)
					.flatMap(p -> workspaceService.getWorkspaceFile(workspace.get(), p.toString()).stream());

			return Flux.fromStream(filesToStore)
					.flatMap(p -> documentClient.uploadDocument(toZeebeStoreDocumentRequest(p, getZeebeJobInfoForWorkspace(workspace)), null))
					.collect(Collectors.toMap(
							r -> r.metadata().fileName(),
							r -> r));
		}));
	}

	private record ZeebeJobInfo(String procesDefinitionId, Long processInstanceKey) {}
	private ZeebeJobInfo getZeebeJobInfoForWorkspace(Optional<Workspace> workspace) {
		return workspace
				.map(w -> w.<ActivatedJob>getProperty("ZEEBE_JOB"))
				.map(j -> new ZeebeJobInfo(
						j.getBpmnProcessId(),
						j.getProcessInstanceKey()))
				.orElse(new ZeebeJobInfo(null, null));
	}

	private static MultiValueMap<String, HttpEntity<?>> toZeebeStoreDocumentRequest(WorkspaceFile file, ZeebeJobInfo zeebeJobInfo) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();

		builder.part("metadata", new ZeebeDocumentDescriptor.Metadata(
						file.contentType(),
						fixSlashes(file.path().getFileName()),
						null,
						file.size(),
						zeebeJobInfo.procesDefinitionId(),
						zeebeJobInfo.processInstanceKey(),
						Collections.emptyMap()))
				.contentType(MediaType.APPLICATION_JSON);

		builder.asyncPart("file",
						DataBufferUtils.read(file.path(), DefaultDataBufferFactory.sharedInstance, 8192),
						DataBuffer.class)
				.contentType(MediaType.parseMediaType(file.contentType()))
				.filename(fixSlashes(file.workspace().path().relativize(file.path())));

		return builder.build();
	}

	record RetrieveFileResult(String result, String details) {}

	@PostMapping("/retrieve/{workspaceId}")
	public Mono<Map<String, RetrieveFileResult>> retrieveFiles(
			@PathVariable String workspaceId,
			@Valid @RequestBody Map<String, @Valid ZeebeDocumentDescriptor> request) {

		return io.supply(() -> workspaceService.getById(workspaceId))
				.flatMap(Mono::justOrEmpty)
				.flatMap(ws -> Flux.fromIterable(request.entrySet())

						.flatMap(kv -> ws.path().resolve(kv.getKey()).normalize().toAbsolutePath().startsWith(ws.path().toAbsolutePath())
								? Mono.just(kv)
								: Mono.error(IllegalArgumentException::new))
						.doOnNext(kv -> io.createDirectories(ws.path().resolve(kv.getKey()).getParent()))

						.flatMap(kv -> io.write(
										documentClient.getDocument(kv.getValue().documentId(), kv.getValue().storeId(), kv.getValue().contentHash()),
										ws.path().resolve(kv.getKey()))

								.then(Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("OK", null))))
								.onErrorResume(FeignException.NotFound.class, _ -> Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("NOT_FOUND", null))))
								.onErrorResume(IllegalArgumentException.class, _ -> Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("BAD_REQUEST", null))))
								.onErrorResume(thrown -> Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("ERROR", "%s: %s".formatted(thrown.getClass().getSimpleName(), thrown.getMessage()))))))

						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
	}
}
