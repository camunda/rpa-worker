package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.robot.RobotExecutionListener
import io.camunda.rpa.worker.robot.RobotService
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.workspace.WorkspaceService
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class ScriptSandboxControllerSpec extends Specification implements PublisherUtils {
	
	RobotService robotService = Mock()
	WorkspaceService workspaceService = Mock()
	
	@Subject
	ScriptSandboxController controller = new ScriptSandboxController(robotService, workspaceService)
	
	void "Executes script from passed-in body and returns result"() {
		given:
		String scriptBody = "the-script-body"
		Map<String, Object> inputVariables = [foo: 'bar']
		Map<String, Object> outputVariables = [baz: 'bat']
		Path workspace = Stub()

		when:
		EvaluateScriptResponse response = block controller.evaluateScript(new EvaluateScriptRequest(scriptBody, inputVariables))
		
		then:
		1 * robotService.execute(new RobotScript("_eval_", scriptBody), inputVariables, [:], null, _) >> { _, __, ___, ____, RobotExecutionListener executionListener ->
			executionListener.afterRobotExecution(workspace)
			return Mono.just(
					new ExecutionResults(
							[main: new ExecutionResults.ExecutionResult("main", ExecutionResults.Result.PASS, "the-output", outputVariables)], null,
							outputVariables))
		}
		
		and:
		response.result() == ExecutionResults.Result.PASS
		response.variables() == outputVariables
		
		and:
		1 * workspaceService.preserveLast(workspace)
	}
}
