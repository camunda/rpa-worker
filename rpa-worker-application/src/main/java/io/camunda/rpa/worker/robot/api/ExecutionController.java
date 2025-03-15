package io.camunda.rpa.worker.robot.api;

import io.camunda.rpa.worker.robot.RobotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/execution")
class ExecutionController {
	
	private final RobotService robotService;
	
	@PostMapping("/{workspaceId}/abort")
	public Mono<? extends ResponseEntity<?>> abort(
			@PathVariable String workspaceId,
			@RequestBody AbortExecutionRequest abortExecutionRequest) {
		
		return Mono.justOrEmpty(robotService.findExecutionByWorkspace(workspaceId))
				.doOnNext(pc -> pc.abort(abortExecutionRequest.silent() != null && abortExecutionRequest.silent()))
				.map(_ -> ResponseEntity.noContent().build())
				.defaultIfEmpty(ResponseEntity.notFound().build());
	}
}
