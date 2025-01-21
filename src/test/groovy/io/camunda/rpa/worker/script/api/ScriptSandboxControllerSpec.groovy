package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.ExecutionResult
import io.camunda.rpa.worker.robot.RobotService
import io.camunda.rpa.worker.script.RobotScript
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ScriptSandboxControllerSpec extends Specification implements PublisherUtils {
	
	RobotService robotService = Mock()
	
	@Subject
	ScriptSandboxController controller = new ScriptSandboxController(robotService)
	
	void "Executes script from passed-in body and returns result"() {
		given:
		String scriptBody = "the-script-body"
		Map<String, Object> inputVariables = [foo: 'bar']
		Map<String, Object> outputVariables = [baz: 'bat']

		when:
		EvaluateScriptResponse response = block controller.evaluateScript(new EvaluateScriptRequest(scriptBody, inputVariables))
		
		then:
		1 * robotService.execute(new RobotScript("<eval>", scriptBody), inputVariables, [:]) >> Mono.just(
				new ExecutionResult(ExecutionResult.Result.PASS, "the-output", outputVariables))
		
		and:
		response.result() == ExecutionResult.Result.PASS
		response.variables() == outputVariables
	}
}
