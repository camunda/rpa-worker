package io.camunda.rpa.worker.zeebe

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ProcessTimeoutException
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.robot.ExecutionResults.Result
import io.camunda.rpa.worker.robot.RobotExecutionListener
import io.camunda.rpa.worker.robot.RobotService
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.script.ScriptRepository
import io.camunda.rpa.worker.secrets.SecretsService
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1
import io.camunda.zeebe.client.api.command.FailJobCommandStep1
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.time.Duration

class ZeebeJobServiceSpec extends Specification implements PublisherUtils {

	static final String TASK_PREFIX = "camunda::RPA-Task::"

	ZeebeClient zeebeClient = Mock()
	
	ZeebeProperties zeebeProperties = new ZeebeProperties(TASK_PREFIX, ["tag-one", "tag-two"].toSet(), "http://auth/".toURI(), 1)
	RobotService robotService = Mock()
	SecretsService secretsService = Stub() {
		getSecrets() >> Mono.just([secretVar: 'secret-value'])
	}
	RobotScript script = new RobotScript("this_script", null)
	ScriptRepository scriptRepository = Stub() {
		findById("this_script_latest") >> Mono.just(script)
		getById("this_script_latest") >> Mono.just(script)
	}
	ObjectMapper objectMapper = new ObjectMapper()
	WorkspaceCleanupService workspaceService = Mock()

	@Subject
	ZeebeJobService service = new ZeebeJobService(
			zeebeClient,
			zeebeProperties,
			robotService,
			scriptRepository,
			objectMapper, 
			secretsService, 
			workspaceService)

	void "Runs received task and reports success"() {
		given:
		ActivatedJob job = anRpaJob()
		Map<String, String> expectedOutputVars = [outputVar: 'output-var-value']
		Map<String, String> expectedExtraEnv = [
				RPA_ZEEBE_PROCESS_INSTANCE_KEY: "345", 
				RPA_ZEEBE_JOB_KEY: "123", 
				RPA_ZEEBE_BPMN_PROCESS_ID: "234",
				RPA_ZEEBE_JOB_TYPE: "camunda::RPA-Task::default"
		]
		
		Path workspacePath = Stub()
		Workspace workspace = new Workspace(null, workspacePath)
		
		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [], [], _, _, null, _, expectedExtraEnv, [(ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY): job]) >> { _, __, ___, ____, _____, ______, RobotExecutionListener executionListener, _______, ________ ->
			executionListener.beforeScriptExecution(workspace, Duration.ofMinutes(1))
			executionListener.afterRobotExecution(workspace)
			return Mono.just(new ExecutionResults(
					[main: new ExecutionResults.ExecutionResult("main", Result.PASS, "", expectedOutputVars)],
					Result.PASS,
					expectedOutputVars, 
					Stub(Path)))
		}
		
		then:
		1 * zeebeClient.newUpdateJobCommand(job) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(Duration.ofMinutes(1)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}

		then:
		1 * zeebeClient.newCompleteCommand(job) >> Mock(CompleteJobCommandStep1) {
			1 * variables(expectedOutputVars) >> it
			1 * send()
		}
		
		and:
		1 * workspaceService.deleteWorkspace(workspace)
	}

	void "Runs received task and reports Robot failure/error"(Result result, String expectedCode) {
		given:
		ActivatedJob job = anRpaJob()
		Map<String, String> expectedOutputVars = [outputVar: 'output-var-value']
		
		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [], [], _, _, null, _, _, _) >> Mono.just(new ExecutionResults([main: new ExecutionResults.ExecutionResult("main", result, "", expectedOutputVars)], 
				result, 
				expectedOutputVars, 
				Stub(Path)))

		and:
		1 * zeebeClient.newThrowErrorCommand(job) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode(expectedCode) >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}

		where:
		result       || expectedCode
		Result.FAIL  || "ROBOT_TASKFAIL"
		Result.ERROR || "ROBOT_ERROR"
	}

	void "Runs received task and reports low level failures"() {
		given:
		ActivatedJob job = anRpaJob()

		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [], [], _, _, null, _, _, _) >> Mono.error(new RuntimeException("Bang!"))

		and:
		1 * zeebeClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(job.retries - 1) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}

	void "Passes variables to Robot execution"() {
		when: "There are specific RPA Input Variables available"
		ActivatedJob job1 = anRpaJob([
				camundaRpaTaskInput: [rpaVar: 'the-value'], otherVar: 'should-not-be-used'])
		block service.handleJob(job1)

		then: "The RPA Input Variables are passed to the Robot execution"
		1 * robotService.execute(script, [], [], [rpaVar: 'the-value'], [SECRET_SECRETVAR: 'secret-value'], null, _, _, _) >> Mono.empty()

		when: "There are NO specific RPA Input Variables available"
		ActivatedJob job2 = anRpaJob([otherVar: 'other-val'])
		block service.handleJob(job2)

		then: "The Job's main variables are passed to the Robot execution"
		1 * robotService.execute(script, [], [], [otherVar: 'other-val'], [SECRET_SECRETVAR: 'secret-value'], null, _, _, _) >> Mono.empty()
	}
	
	void "Errors when can't find script in headers"() {
		given:
		RobotScript script = new RobotScript("this_script", null)
		scriptRepository.findById("this_script_latest") >> Mono.just(script)

		and:
		ActivatedJob job = Stub()

		when:
		block service.handleJob(job)

		then:
		1 * zeebeClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(job.retries - 1) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}
	
	void "Includes before and after scripts when present"() {
		given:
		ActivatedJob job = anRpaJob([:], [
		        new ZeebeLinkedResource(
				        "before_1", 
				        ZeebeBindingType.latest, 
				        "RPA", 
				        "?", 
				        ZeebeJobService.BEFORE_SCRIPT_LINK_NAME, 
				        "before_1_latest"),

		        new ZeebeLinkedResource(
				        "after_1",
				        ZeebeBindingType.latest,
				        "RPA",
				        "?",
				        ZeebeJobService.AFTER_SCRIPT_LINK_NAME,
				        "after_1_latest"),
		])

		and:
		RobotScript expectedBefore = new RobotScript("before_1", null)
		RobotScript expectedAfter = new RobotScript("after_1", null)
		scriptRepository.getById("before_1_latest") >> Mono.just(expectedBefore)
		scriptRepository.getById("after_1_latest") >> Mono.just(expectedAfter)
		
		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [expectedBefore], [expectedAfter], _, _, null, _, _, _) >> Mono.just(new ExecutionResults(
				[main: new ExecutionResults.ExecutionResult("main", Result.PASS, "", [:])], null, [:], Stub(Path)))
	}
	
	void "Sets timeout when present, reports correct error when exceeded"() {
		given:
		ActivatedJob job = anRpaJob([:], [], [(ZeebeJobService.TIMEOUT_HEADER_NAME): "PT5M"])
		
		when:
		block service.handleJob(job)
		
		then: 
		1 * robotService.execute(script, [], [], [:], [SECRET_SECRETVAR: 'secret-value'], Duration.ofMinutes(5), _, _, _) >> {
			throw new ProcessTimeoutException("", "")
		}
		
		and:
		1 * zeebeClient.newThrowErrorCommand(job) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_TIMEOUT") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}

	private ActivatedJob anRpaJob(Map<String, Object> variables = [:], List additionalResources = [], Map additionalHeaders = [:]) {
		return Stub(ActivatedJob) {
			getCustomHeaders() >> [
					(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson([
									new ZeebeLinkedResource(
											"this_script",
											ZeebeBindingType.latest,
											"RPA",
											"?",
											ZeebeJobService.MAIN_SCRIPT_LINK_NAME,
											"this_script_latest"),

									*additionalResources
							]),
					
					*: additionalHeaders
			]

			getVariablesAsMap() >> variables
			getKey() >> 123
			getBpmnProcessId() >> "234"
			getProcessInstanceKey() >> 345
			getRetries() >> 3
			getType() >> "camunda::RPA-Task::default"
		}
	}
}
