package io.camunda.rpa.worker.workspace.api;

import io.camunda.rpa.worker.workspace.WorkspaceVariablesManager;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException;

@Controller
@RequestMapping("/workspace")
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class WorkspaceVariablesController {
	
	private final WorkspaceVariablesManager workspaceVariablesManager;

	@PostMapping("{workspaceId}/variables")
	public Mono<? extends ResponseEntity<?>> attachVariables(
			@PathVariable String workspaceId,
			@RequestBody @Valid AttachVariablesRequest request) {
		
		return workspaceVariablesManager.attachVariables(workspaceId, request.variables())
				.then(Mono.just(ResponseEntity.ok().build()))
				.onErrorReturn(NoSuchElementException.class, ResponseEntity.notFound().build());
	}
}
