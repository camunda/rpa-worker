package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.robot.ExecutionResults;
import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService;
import io.camunda.rpa.worker.workspace.WorkspaceFile;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/script/evaluate")
@RequiredArgsConstructor
class ScriptSandboxController {
	
	private final RobotService robotService;
	private final WorkspaceCleanupService workspaceCleanupService;
	private final WorkspaceService workspaceService;
	private final IO io;
	private final Environment environment;

	@PostMapping
	public Mono<EvaluateScriptResponse> evaluateScript(@RequestBody @Valid EvaluateScriptRequest request, ServerWebExchange exchange) {
		RobotScript robotScript = new RobotScript("_eval_", request.script());
		return robotService.execute(robotScript, request.variables(), Collections.emptyMap(), null, workspaceCleanupService::preserveLast)
				.flatMap(xr -> io.supply(() -> workspaceService.getWorkspaceFiles(xr.workspace().getFileName().toString()))
						.map(wsFiles -> {
							URI serverUrl = getServerUrlBestGuess(exchange);
							Map<String, URI> workspace = wsFiles.collect(Collectors.toMap(
									p -> "/" + xr.workspace().relativize(p.path()).toString().replaceAll("\\\\", "/"),
									p -> attachIfNecessary(p, serverUrl
											.resolve("/workspace/%s/".formatted(xr.workspace().getFileName().toString()))
											.resolve(xr.workspace().relativize(p.path()).toString().replaceAll("\\\\", "/")))));

							ExecutionResults.ExecutionResult r = xr.results().entrySet().iterator().next().getValue();
							return new EvaluateScriptResponse(r.result(), r.output(), xr.outputVariables(), workspace);
						}));
	}

	private URI getServerUrlBestGuess(ServerWebExchange exchange) {
		String scheme = Optional.ofNullable(exchange.getRequest().getSslInfo())
				.map(_ -> "https")
				.orElse("http");
		
		String host = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(HttpHeaders.SERVER))
				.or(() -> Optional.ofNullable(environment.getProperty("server.address"))
						.filter(it -> ! it.equals("0.0.0.0"))
						.filter(it -> ! it.equals("::/0")))
				.orElse("localhost");
		
		int port = Optional.ofNullable(environment.getProperty("server.port", Integer.class))
				.filter(it -> it != 0)
				.or(()  -> Optional.ofNullable(environment.getProperty("local.server.port", Integer.class)))
				.orElse(36227);
		
		return URI.create("%s://%s:%s/".formatted(scheme, host, port));
	}

	private static final Set<MediaType> BROWSER_FRIENDLY_MEDIA_TYPES = Set.of(
			MediaType.parseMediaType("text/*"), 
			MediaType.parseMediaType("image/*"),
			MediaType.APPLICATION_PDF, 
			MediaType.APPLICATION_JSON, 
			MediaType.APPLICATION_XML);

	private URI attachIfNecessary(WorkspaceFile file, URI uri) {
		MediaType mediaType = MediaType.parseMediaType(file.contentType());
		
		return BROWSER_FRIENDLY_MEDIA_TYPES.stream()
				.filter(mediaType::isCompatibleWith)
				.findFirst()
				.map(_ -> uri)
				.orElseGet(() -> UriComponentsBuilder.fromUri(uri).query("attachment").build().toUri());
	}
}
