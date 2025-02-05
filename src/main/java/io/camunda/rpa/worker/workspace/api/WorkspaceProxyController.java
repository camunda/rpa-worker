package io.camunda.rpa.worker.workspace.api;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.workspace.WorkspaceFile;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;

@Controller
@RequestMapping("/workspace")
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class WorkspaceProxyController {
	
	private static final Function<Path, Flux<DataBuffer>> defaultDataBuffersFactory = 
			path -> DataBufferUtils.read(path, DefaultDataBufferFactory.sharedInstance, 8192);
	
	private final WorkspaceService workspaceService;
	private final Function<Path, Flux<DataBuffer>> dataBuffersFactory;
	private final IO io;

	@Autowired
	public WorkspaceProxyController(WorkspaceService workspaceService, IO io) {
		this(workspaceService, defaultDataBuffersFactory, io);
	}

	@GetMapping(value = "/{workspaceId}/**")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getWorkspaceFileContents(@PathVariable String workspaceId, ServerWebExchange exchange) {
		return getWorkspaceFile(workspaceId, exchange, (b, _) -> b);
	}

	@GetMapping(value = "/{workspaceId}/**", params = "attachment")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getWorkspaceFileAttachment(@PathVariable String workspaceId, ServerWebExchange exchange) {
		return getWorkspaceFile(workspaceId, exchange, (b, wf) -> b
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"%s\"".formatted(wf.path().getFileName().toString())));
	}
	
	private Mono<ResponseEntity<Flux<DataBuffer>>> getWorkspaceFile(
			String workspaceId,
			ServerWebExchange exchange, 
			BiFunction<ResponseEntity.BodyBuilder, WorkspaceFile, ResponseEntity.BodyBuilder> customiser) {

		String requestedFilePath = exchange.getRequest().getPath().pathWithinApplication().subPath(5).value();

		return io.supply(() -> workspaceService.getWorkspaceFile(workspaceId, requestedFilePath))
				.flatMap(Mono::justOrEmpty)
				.map(workspaceFile -> customiser.apply(ResponseEntity.ok()
						.header(HttpHeaders.CONTENT_TYPE, workspaceFile.contentType())
						.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(workspaceFile.size())), workspaceFile)
						.body(dataBuffersFactory.apply(workspaceFile.path())))
				.switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
	}
}
