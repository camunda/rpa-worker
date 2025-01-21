package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.script.RobotScript;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;

@RestController
@RequestMapping("/script/evaluate")
@RequiredArgsConstructor
class ScriptSandboxController {
	
	private final RobotService robotService;
	
	@PostMapping
	public Mono<EvaluateScriptResponse> evaluateScript(@RequestBody @Valid EvaluateScriptRequest request) {
		RobotScript robotScript = new RobotScript("<eval>", request.script());
		return robotService.execute(robotScript, request.variables(), Collections.emptyMap())
				.map(xr -> new EvaluateScriptResponse(xr.result(), xr.output(), xr.outputVariables()));
	}
}
