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
import io.camunda.rpa.worker.zeebe.RpaResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.function.Supplier
import java.util.stream.Stream

class ScriptSandboxControllerSpec extends Specification implements PublisherUtils {

	RobotService robotService = Mock()
	WorkspaceCleanupService workspaceCleanupService = Mock()
	WorkspaceService workspaceService = Stub()
	IO io = Stub() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}

	void "Executes raw script from passed-in body and returns result"() {
		given:
		@Subject
		ScriptSandboxController controller = new ScriptSandboxController(
				robotService, 
				workspaceCleanupService,
				workspaceService, 
				io, 
				new ScriptSandboxProperties(true))

		and:
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
		EvaluateScriptResponse response = block controller.evaluateRawScript(new EvaluateRawScriptRequest(scriptBody, inputVariables, null))
				.map { it.body }
		
		then:
		1 * robotService.execute(RobotScript.builder().id("_eval_").body(scriptBody).build(), inputVariables, null, _, null) >> { _, __, ___, List<RobotExecutionListener> executionListeners, ____ ->
			executionListeners*.afterRobotExecution(workspace)

			return Mono.just(
					new ExecutionResults(
							[main: new ExecutionResults.ExecutionResult("main", ExecutionResults.Result.PASS, "the-output", outputVariables, Duration.ZERO)], null,
							outputVariables,
							workspaceDir, Duration.ZERO))
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

	void "Executes script from passed-in RPA Resource and returns result"() {
		given:
		@Subject
		ScriptSandboxController controller = new ScriptSandboxController(
				robotService,
				workspaceCleanupService,
				workspaceService,
				io,
				new ScriptSandboxProperties(true))

		and:
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
		EvaluateScriptResponse response = block controller.evaluateRichScript(new EvaluateRichScriptRequest(
				RpaResource.builder().script(scriptBody).build(), 
				inputVariables, 
				null))
				.map { it.body }

		then:
		1 * robotService.execute(RobotScript.builder().id("_eval_").body(scriptBody).build(), inputVariables, null, _, null) >> { _, __, ___, List<RobotExecutionListener> executionListeners, ____ ->
			executionListeners*.afterRobotExecution(workspace)

			return Mono.just(
					new ExecutionResults(
							[main: new ExecutionResults.ExecutionResult("main", ExecutionResults.Result.PASS, "the-output", outputVariables, Duration.ZERO)], null,
							outputVariables,
							workspaceDir, Duration.ZERO))
		}

		and:
		response.result() == ExecutionResults.Result.PASS
		response.variables() == outputVariables
		response.workspace() == [
				"/output/file1.txt" : "/workspace/workspace123/output/file1.txt".toURI(),
				"/output/file2.xlsx": "/workspace/workspace123/output/file2.xlsx?attachment".toURI(),
		]

		and:
		1 * workspaceCleanupService.preserveLast(workspace)
	}

	void "Returns not found and does not run scripts when Sandbox is disabled"() {
		given:
		@Subject
		ScriptSandboxController controller = new ScriptSandboxController(
				robotService,
				workspaceCleanupService,
				workspaceService,
				io,
				new ScriptSandboxProperties(false))
		
		when:
		ResponseEntity<EvaluateScriptResponse> response = block controller.evaluateRawScript(new EvaluateRawScriptRequest("", [:], null))
		
		then:
		0 * robotService._
		
		and:
		response.statusCode == HttpStatus.NOT_FOUND
	}
}