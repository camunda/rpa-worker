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
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1
import io.camunda.zeebe.client.api.command.FailJobCommandStep1
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.time.Duration

class ZeebeJobServiceSpec extends Specification implements PublisherUtils {

	ZeebeClient zeebeClient = Mock()

	RobotService robotService = Mock()
	RobotScript script = RobotScript.builder().id("this_script").body(null).build()
	ScriptRepository scriptRepository = Stub() {
		findById("this_script_latest") >> Mono.just(script)
		getById("this_script_latest") >> Mono.just(script)
	}
	ObjectMapper objectMapper = new ObjectMapper()
	WorkspaceCleanupService workspaceService = Mock()
	ZeebeMetricsService metricsService = Mock()

	@Subject
	ZeebeJobService service = new ZeebeJobService(
			zeebeClient,
			robotService,
			scriptRepository,
			objectMapper, 
			workspaceService, 
			metricsService)

	void "Runs received task and reports success"() {
		given:
		ActivatedJob job = anRpaJob()
		Map<String, String> expectedOutputVars = [outputVar: 'output-var-value']
		
		Path workspacePath = Stub()
		Workspace workspace = new Workspace(null, workspacePath)
		
		when:
		block service.handleJob(job)

		then:
		1 * metricsService.onZeebeJobReceived(job.type)
		1 * robotService.execute(script, [], [], _, null, _, [(ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY): job], null) >> { _, __, ___, ____, _____, List<RobotExecutionListener> executionListeners, _______, ________ ->
			executionListeners*.beforeScriptExecution(workspace, Duration.ofMinutes(1))
			executionListeners*.afterRobotExecution(workspace)
			return Mono.just(new ExecutionResults(
					[main: new ExecutionResults.ExecutionResult("main", Result.PASS, "", expectedOutputVars, Duration.ofSeconds(3))],
					Result.PASS,
					expectedOutputVars,
					Stub(Path),
					Duration.ofSeconds(3)))
		}
		
		then:
		1 * zeebeClient.newUpdateJobCommand(job) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(Duration.ofMinutes(1)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}

		then:
		1 * zeebeClient.newCompleteCommand(job) >> Mock(CompleteJobCommandStep1) {
			1 * send()
		}
		
		and:
		1 * metricsService.onZeebeJobSuccess("camunda::RPA-Task::default", Duration.ofSeconds(3))
		
		and:
		1 * workspaceService.deleteWorkspace(workspace)
		
		and:
		1 * zeebeClient.newSetVariablesCommand(job.processInstanceKey) >> Mock(SetVariablesCommandStep1) {
			1 * variables(expectedOutputVars) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}
	}

	void "Runs received task and reports Robot failure/error"(Result result, String expectedMessage) {
		given:
		ActivatedJob job = anRpaJob()
		Map<String, String> expectedOutputVars = [outputVar: 'output-var-value']
		
		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [], [], _, null, _, _, null) >> Mono.just(new ExecutionResults([main: new ExecutionResults.ExecutionResult("main", result, "", expectedOutputVars, Duration.ZERO)],
				result, 
				expectedOutputVars, 
				Stub(Path), 
				Duration.ZERO))

		and:
		1 * zeebeClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(job.retries - 1) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ m -> m.startsWith(expectedMessage) }) >> it
				1 * send()
			}
		}
		
		and:
		1 * metricsService.onZeebeJobFail(job.type, _)

		and:
		1 * zeebeClient.newSetVariablesCommand(job.processInstanceKey) >> Mock(SetVariablesCommandStep1) {
			1 * variables(expectedOutputVars) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}

		where:
		result       || expectedMessage
		Result.FAIL  || "There were task failures"
		Result.ERROR || "There were task errors"
	}

	void "Runs received task and reports low level failures"() {
		given:
		ActivatedJob job = anRpaJob()

		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [], [], _, null, _, _, null) >> Mono.error(new RuntimeException("Bang!"))

		and:
		1 * zeebeClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(job.retries - 1) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
		
		and:
		1 * metricsService.onZeebeJobError(job.type)
	}

	void "Passes variables to Robot execution"() {
		when: "There are specific RPA Input Variables available"
		ActivatedJob job1 = anRpaJob([
				camundaRpaTaskInput: [rpaVar: 'the-value'], otherVar: 'should-not-be-used'])
		block service.handleJob(job1)

		then: "The RPA Input Variables are passed to the Robot execution"
		1 * robotService.execute(script, [], [], [rpaVar: 'the-value'], null, _, _, null) >> Mono.empty()

		when: "There are NO specific RPA Input Variables available"
		ActivatedJob job2 = anRpaJob([otherVar: 'other-val'])
		block service.handleJob(job2)

		then: "The Job's main variables are passed to the Robot execution"
		1 * robotService.execute(script, [], [], [otherVar: 'other-val'], null, _, _, null) >> Mono.empty()
	}
	
	void "Errors when can't find script in headers"() {
		given:
		RobotScript script = RobotScript.builder().id("this_script").build()
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
		RobotScript expectedBefore = RobotScript.builder().id("before_1").build()
		RobotScript expectedAfter = RobotScript.builder().id("after_1").build()
		scriptRepository.getById("before_1_latest") >> Mono.just(expectedBefore)
		scriptRepository.getById("after_1_latest") >> Mono.just(expectedAfter)
		
		when:
		block service.handleJob(job)

		then:
		1 * robotService.execute(script, [expectedBefore], [expectedAfter], _, null, _, _, null) >> Mono.just(new ExecutionResults(
				[main: new ExecutionResults.ExecutionResult("main", Result.PASS, "", [:], Duration.ZERO)], null, [:], Stub(Path), Duration.ZERO))
	}
	
	void "Sets timeout when present, reports correct error when exceeded"() {
		given:
		ActivatedJob job = anRpaJob([:], [], [(ZeebeJobService.TIMEOUT_HEADER_NAME): "PT5M"])
		
		when:
		block service.handleJob(job)
		
		then: 
		1 * robotService.execute(script, [], [], [:], Duration.ofMinutes(5), _, _, null) >> {
			throw new ProcessTimeoutException("", "")
		}
		
		and:
		1 * zeebeClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(job.retries - 1) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ m -> m.startsWith("The execution timed out") }) >> it
				1 * send()
			}
		}

		and:
		1 * metricsService.onZeebeJobFail(job.type, "ROBOT_TIMEOUT")
	}

	void "Does not process results of detached execution"() {
		given:
		ActivatedJob job = anRpaJob()
		Map<String, String> expectedOutputVars = [outputVar: 'output-var-value']

		Path workspacePath = Stub()
		Workspace workspace = new Workspace(null, workspacePath)

		when:
		block service.handleJob(job)

		then:
		1 * metricsService.onZeebeJobReceived(job.type)
		1 * robotService.execute(script, [], [], _, null, _, [(ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY): job], null) >> { _, __, ___, ____, _____, List<RobotExecutionListener> executionListeners, _______, ________ ->
			executionListeners*.beforeScriptExecution(workspace, Duration.ofMinutes(1))
			executionListeners*.afterRobotExecution(workspace)
			service.pushDetached(123L)
			return Mono.just(new ExecutionResults(
					[main: new ExecutionResults.ExecutionResult("main", Result.PASS, "", [:], Duration.ofSeconds(3))],
					Result.FAIL,
					expectedOutputVars,
					Stub(Path),
					Duration.ofSeconds(3)))
		}

		and:
		1 * zeebeClient.newUpdateJobCommand(job) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(Duration.ofMinutes(1)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}

		then:
		0 * zeebeClient._(*_)

		and:
		1 * workspaceService.deleteWorkspace(workspace)
		
		then:
		1 * zeebeClient.newSetVariablesCommand(job.processInstanceKey) >> Mock(SetVariablesCommandStep1) {
			1 * variables(expectedOutputVars) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
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
			getElementInstanceKey() >> 456
		}
	}
}
