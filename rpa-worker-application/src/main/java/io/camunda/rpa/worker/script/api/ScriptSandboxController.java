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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.rpa.worker.util.PathUtils.fixSlashes;

@RestController
@RequestMapping("/script/evaluate")
@RequiredArgsConstructor
@Slf4j
class ScriptSandboxController {
	
	private final RobotService robotService;
	private final WorkspaceCleanupService workspaceCleanupService;
	private final WorkspaceService workspaceService;
	private final IO io;
	private final ScriptSandboxProperties sandboxProperties;

	@PostMapping
	public Mono<ResponseEntity<EvaluateScriptResponse>> evaluateScript(@RequestBody @Valid EvaluateScriptRequest request) {

		if ( ! sandboxProperties.enabled())
			return Mono.just(ResponseEntity.notFound().build());

		return doEvaluateScript(request)
				.map(ResponseEntity::ok);
	}

	private Mono<EvaluateScriptResponse> doEvaluateScript(EvaluateScriptRequest request) {
		log.atInfo().log("Received script for sandbox evaluation");

		RobotScript robotScript = new RobotScript("_eval_", request.script());
		return robotService.execute(robotScript, request.variables(), null, Collections.singletonList(workspaceCleanupService::preserveLast), request.workspaceAffinityKey())

				.doOnSuccess(xr -> log.atInfo().kv("result", xr.result()).log("Returning sandbox execution results"))

				.flatMap(xr -> getWorkspaceFileListWithProxyUrls(xr).map(files -> {
					ExecutionResults.ExecutionResult r = xr.results().entrySet().iterator().next().getValue();
					return new EvaluateScriptResponse(r.result(), r.output(), xr.outputVariables(), files);
				}))

				.doOnError(thrown -> log.atError().setCause(thrown).log("Error running sandbox script"));
	}
	
	private Mono<Map<String, URI>> getWorkspaceFileListWithProxyUrls(ExecutionResults xr) {
		return io.supply(() -> workspaceService.getWorkspaceFiles(xr.workspace().getFileName().toString()))
				.map(wsFiles -> wsFiles.collect(Collectors.toMap(
						p -> "/" + fixSlashes(xr.workspace().relativize(p.path())),
						p -> attachIfNecessary(p, URI.create("/")
								.resolve("/workspace/%s/".formatted(xr.workspace().getFileName().toString()))
								.resolve(fixSlashes(xr.workspace().relativize(p.path())))))));
	}

	private static final Set<MediaType> BROWSER_FRIENDLY_MEDIA_TYPES = Set.of(
			MediaType.parseMediaType("text/*"), 
			MediaType.parseMediaType("image/*"),
			MediaType.APPLICATION_PDF, 
			MediaType.APPLICATION_JSON, 
			MediaType.APPLICATION_XML, 
			MediaType.APPLICATION_YAML);

	private URI attachIfNecessary(WorkspaceFile file, URI uri) {
		MediaType mediaType = MediaType.parseMediaType(file.contentType());
		
		return BROWSER_FRIENDLY_MEDIA_TYPES.stream()
				.filter(mediaType::isCompatibleWith)
				.findFirst()
				.map(_ -> uri)
				.orElseGet(() -> UriComponentsBuilder.fromUri(uri).query("attachment").build().toUri());
	}
}
