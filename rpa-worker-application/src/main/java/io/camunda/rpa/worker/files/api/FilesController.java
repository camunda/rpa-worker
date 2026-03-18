package io.camunda.rpa.worker.files.api;

import feign.FeignException;
import io.camunda.rpa.worker.api.StubbedResponseGenerator;
import io.camunda.rpa.worker.files.FilesService;
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.workspace.Workspace;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import io.camunda.rpa.worker.zeebe.ZeebeJobInfo;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.camunda.rpa.worker.util.PathUtils.fixSlashes;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
class FilesController {

	private final WorkspaceService workspaceService;
	private final IO io;
	private final StubbedResponseGenerator stubbedResponseGenerator;
	private final FilesService filesService;

	@PostMapping("/store/{workspaceId}")
	public Mono<ResponseEntity<?>> storeFiles(
			@PathVariable String workspaceId,
			@Valid @RequestBody StoreFilesRequest request) {

		return io.wrap(Mono.defer(() -> Mono.justOrEmpty(workspaceService.getById(workspaceId))
				.flatMap(w -> Flux.fromStream(() -> {
							PathMatcher pathMatcher = io.globMatcher(URI.create(fixSlashes(request.files()).replaceAll(fixSlashes(w.path().toAbsolutePath()) + "/?", "")).normalize().toString());
							return io.walk(w.path())
									.map(p -> w.path().relativize(p))
									.filter(pathMatcher::matches)
									.flatMap(p -> workspaceService.getWorkspaceFile(w, p.toString()).stream());
						})

						.collect(Collectors.toMap(
								it -> it,
								it -> FilesService.toMetadata(it, getZeebeJobInfoForWorkspace(w))))

						.flatMap(files -> stubbedResponseGenerator
								.stubbedResponse("DocumentClient", "uploadDocument", files.entrySet().stream()
										.collect(Collectors.toMap(
												kv -> w.path().relativize(kv.getKey().path()),
												Map.Entry::getValue)))
								.switchIfEmpty(Flux.fromStream(files.entrySet().stream())
										
										.flatMap(kv -> 
												filesService.uploadDocument(kv.getKey(), kv.getValue()))
										
										.collect(Collectors.toMap(
												(ZeebeDocumentDescriptor zdd) -> zdd.metadata().fileName(),
												((ZeebeDocumentDescriptor zdd) -> zdd)))

										.map(ResponseEntity::ok))))));
	}

	private ZeebeJobInfo getZeebeJobInfoForWorkspace(Workspace workspace) {
		return Optional.of(workspace)
				.map(w -> w.<ActivatedJob>getProperty("ZEEBE_JOB"))
				.map(j -> new ZeebeJobInfo(
						j.getBpmnProcessId(),
						j.getProcessInstanceKey()))
				.orElse(new ZeebeJobInfo(null, null));
	}

	record RetrieveFileResult(String result, String details) {}

	@PostMapping("/retrieve/{workspaceId}")
	public Mono<ResponseEntity<?>> retrieveFiles(
			@PathVariable String workspaceId,
			@Valid @RequestBody Map<String, @Valid ZeebeDocumentDescriptor> request) {

		return stubbedResponseGenerator.stubbedResponse("DocumentClient", "getDocument", request)
				.switchIfEmpty(Mono.defer(() -> doRetrieveFiles(workspaceId, request)));
	}

	private Mono<ResponseEntity<Map<String, RetrieveFileResult>>> doRetrieveFiles (
				@PathVariable String workspaceId,
				Map<String, ZeebeDocumentDescriptor > request) {
		
			return io.supply(() -> workspaceService.getById(workspaceId))
				.flatMap(Mono::justOrEmpty)
				.flatMap(ws -> Flux.fromIterable(request.entrySet())

						.flatMap(kv -> ws.path().resolve(kv.getKey()).normalize().toAbsolutePath().startsWith(ws.path().toAbsolutePath())
								? Mono.just(kv)
								: Mono.error(IllegalArgumentException::new))
						.doOnNext(kv -> io.createDirectories(ws.path().resolve(kv.getKey()).getParent()))

						.flatMap(kv -> io.write(
										filesService.getDocument(kv.getValue().documentId(), kv.getValue().storeId(), kv.getValue().contentHash()),
										ws.path().resolve(kv.getKey()))

								.then(Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("OK", null))))
								.onErrorResume(FeignException.NotFound.class, _ -> Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("NOT_FOUND", null))))
								.onErrorResume(IllegalArgumentException.class, _ -> Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("BAD_REQUEST", null))))
								.onErrorResume(thrown -> Mono.just(Map.entry(kv.getKey(),
										new RetrieveFileResult("ERROR", "%s: %s".formatted(thrown.getClass().getSimpleName(), thrown.getMessage()))))))

						.collect(Collectors.toMap(Map.Entry::getKey, java.util.Map.Entry::getValue)))
					.map(ResponseEntity::ok);
	}
}
