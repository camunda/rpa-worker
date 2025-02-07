package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.robot.RobotExecutionListener
import io.camunda.rpa.worker.robot.RobotService
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.workspace.WorkspaceService
import org.springframework.core.env.Environment
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier
import java.util.stream.Stream

class ScriptSandboxControllerSpec extends Specification implements PublisherUtils {
	
	RobotService robotService = Mock()
	WorkspaceCleanupService workspaceCleanupService = Mock()
	WorkspaceService workspaceService = Stub()
	IO io = Stub() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	Environment environment = Stub() {
		getProperty("server.port", Integer) >> 36227
		getProperty(_) >> null
	}
	
	@Subject
	ScriptSandboxController controller = new ScriptSandboxController(robotService, workspaceCleanupService, workspaceService, io, environment)
	
	void "Executes script from passed-in body and returns result"() {
		given:
		String scriptBody = "the-script-body"
		Map<String, Object> inputVariables = [foo: 'bar']
		Map<String, Object> outputVariables = [baz: 'bat']
		Path workspaceDir = Paths.get("/path/to/workspace123/")
		Workspace workspace = new Workspace("workspace123", workspaceDir)

		and:
		Path workspaceFile1 = workspaceDir.resolve("output/file1.txt")
		Path workspaceFile2 = workspaceDir.resolve("output/file2.xlsx")
		workspaceService.getWorkspaceFiles("workspace123") >> {
			Stream.of(
					new WorkspaceFile(workspace, "text/plain", 123, workspaceFile1),
					new WorkspaceFile(workspace, "application/octet-stream", 456, workspaceFile2))
		}

		when:
		EvaluateScriptResponse response = block controller.evaluateScript(new EvaluateScriptRequest(scriptBody, inputVariables))
		
		then:
		1 * robotService.execute(new RobotScript("_eval_", scriptBody), inputVariables, [:], null, _) >> { _, __, ___, ____, RobotExecutionListener executionListener ->
			executionListener.afterRobotExecution(workspace)

			return Mono.just(
					new ExecutionResults(
							[main: new ExecutionResults.ExecutionResult("main", ExecutionResults.Result.PASS, "the-output", outputVariables)], null,
							outputVariables,
							workspaceDir))
		}
		
		and:
		response.result() == ExecutionResults.Result.PASS
		response.variables() == outputVariables
		response.workspace() == [
		        "/output/file1.txt": "/workspace/workspace123/output/file1.txt".toURI(),
		        "/output/file2.xlsx": "/workspace/workspace123/output/file2.xlsx?attachment".toURI(),
		]
		
		and:
		1 * workspaceCleanupService.preserveLast(workspace)
	}
}
