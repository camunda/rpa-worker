package io.camunda.rpa.worker.script.api;

import io.camunda.rpa.worker.robot.ExecutionResults;
import io.camunda.rpa.worker.robot.RobotService;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.workspace.WorkspaceService;
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
	private final WorkspaceService workspaceService;
	
	@PostMapping
	public Mono<EvaluateScriptResponse> evaluateScript(@RequestBody @Valid EvaluateScriptRequest request) {
		RobotScript robotScript = new RobotScript("_eval_", request.script());
		return robotService.execute(robotScript, request.variables(), Collections.emptyMap(), null, workspaceService::preserveLast)
				.map(xr -> {
					ExecutionResults.ExecutionResult r = xr.results().entrySet().iterator().next().getValue();
					return new EvaluateScriptResponse(r.result(), r.output(), xr.outputVariables());
				});
	}
}
