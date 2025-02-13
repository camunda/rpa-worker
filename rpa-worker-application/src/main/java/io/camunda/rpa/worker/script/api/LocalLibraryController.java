package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.script.ConfiguredScriptRepository;
import io.camunda.rpa.worker.script.RobotScript;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/script/deploy")
class LocalLibraryController {
	
	private final ConfiguredScriptRepository scriptRepository;
	
	@PostMapping
	public Mono<ResponseEntity<?>> deployScript(@Valid @RequestBody DeployScriptRequest request) {
		return scriptRepository.save(new RobotScript(request.id(), request.script()))
				.thenReturn(ResponseEntity.noContent().build());
	}
}
