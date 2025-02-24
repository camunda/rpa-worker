package io.camunda.rpa.worker.zeebe

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ProcessTimeoutException
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.robot.ExecutionResults.Result
import io.camunda.rpa.worker.robot.RobotExecutionListener
import io.camunda.rpa.worker.robot.RobotService
import io.camunda.rpa.worker.script.ConfiguredScriptRepository
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.secrets.SecretsService
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.ZeebeFuture
import io.camunda.zeebe.client.api.command.*
import io.camunda.zeebe.client.api.response.ActivateJobsResponse
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

class ZeebeJobServiceSpec extends Specification implements PublisherUtils {

	static final String TASK_PREFIX = "camunda::RPA-Task::"

	BlockingQueue<ActivatedJob> jobQueue = new LinkedBlockingQueue<>()
	
	FinalCommandStep activateFinal = Stub() {
		send() >> Stub(ZeebeFuture) {
			handle(_) >> { BiFunction fn ->
				ActivatedJob job = jobQueue.poll(200, TimeUnit.MILLISECONDS)
				return CompletableFuture.completedFuture(
					{ -> job ? [job] : [] } as ActivateJobsResponse).handle(fn)
			}
		}
	}

	ActivateJobsCommandStep1.ActivateJobsCommandStep3 activate3 = Stub() {
		requestTimeout(ZeebeJobService.JOB_POLL_TIME) >> activateFinal
	}

	ActivateJobsCommandStep1.ActivateJobsCommandStep2 activate2 = Stub() {
		maxJobsToActivate(1) >> activate3
	}

	ActivateJobsCommandStep1 activate1 = Mock()

	ZeebeClient zeebeClient = Mock() {
		newActivateJobsCommand() >> { activate1 }
	}
	
	ZeebeProperties zeebeProperties = new ZeebeProperties(TASK_PREFIX, ["tag-one", "tag-two"].toSet(), "http://auth/".toURI(), 1)
	RobotService robotService = Mock()
	SecretsService secretsService = Stub() {
		getSecrets() >> Mono.just([secretVar: 'secret-value'])
	}
	RobotScript script = new RobotScript("this_script", null)
	ConfiguredScriptRepository scriptRepository = Stub() {
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

	void "Starts polling all tags on init"() {
		when:
		service.doInit()
		
		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-one") >> activate2
		
		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-two") >> activate2
		
		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		then:
		_ * activate1._
	}

	void "Runs received task and reports success"() {
		given:
		ActivatedJob job = anRpaJob()
		Map<String, String> expectedOutputVars = [outputVar: 'output-var-value']
		Map<String, String> expectedExtraEnv = [RPA_ZEEBE_PROCESS_INSTANCE_KEY: "345", RPA_ZEEBE_JOB_KEY: "123", RPA_ZEEBE_BPMN_PROCESS_ID: "234"]
		Path workspacePath = Stub()
		Workspace workspace = new Workspace(null, workspacePath)
		
		and:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when:
		jobQueue << job
		service.doInit()

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
		
		and:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when:
		jobQueue << job
		service.doInit()

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
		
		and:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when:
		jobQueue << job
		service.doInit()

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
		given:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when: "There are specific RPA Input Variables available"
		ActivatedJob job1 = anRpaJob([
				camundaRpaTaskInput: [rpaVar: 'the-value'], otherVar: 'should-not-be-used'])
		jobQueue << job1
		service.doInit()

		then: "The RPA Input Variables are passed to the Robot execution"
		1 * robotService.execute(script, [], [], [rpaVar: 'the-value'], [SECRET_SECRETVAR: 'secret-value'], null, _, _, _) >> Mono.empty()

		when: "There are NO specific RPA Input Variables available"
		ActivatedJob job2 = anRpaJob([otherVar: 'other-val'])
		jobQueue << job2
		service.doInit()

		then: "The Job's main variables are passed to the Robot execution"
		1 * robotService.execute(script, [], [], [otherVar: 'other-val'], [SECRET_SECRETVAR: 'secret-value'], null, _, _, _) >> Mono.empty()
	}
	
	void "Errors when can't find script in headers"() {
		given:
		RobotScript script = new RobotScript("this_script", null)
		scriptRepository.findById("this_script_latest") >> Mono.just(script)

		and:
		ActivatedJob job = Stub()

		and:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when:
		jobQueue << job
		service.doInit()

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
		        new ZeebeLinkedResources.ZeebeLinkedResource(
				        "before_1", 
				        ZeebeBindingType.latest, 
				        "RPA", 
				        "?", 
				        ZeebeJobService.BEFORE_SCRIPT_LINK_NAME, 
				        "before_1_latest"),

		        new ZeebeLinkedResources.ZeebeLinkedResource(
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
		
		and:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when:
		jobQueue << job
		service.doInit()

		then:
		1 * robotService.execute(script, [expectedBefore], [expectedAfter], _, _, null, _, _, _) >> Mono.just(new ExecutionResults(
				[main: new ExecutionResults.ExecutionResult("main", Result.PASS, "", [:])], null, [:], Stub(Path)))
	}
	
	void "Sets timeout when present, reports correct error when exceeded"() {
		given:
		ActivatedJob job = anRpaJob([:], [], [(ZeebeJobService.TIMEOUT_HEADER_NAME): "PT5M"])
		
		and:
		activate1.jobType(TASK_PREFIX + "tag-one") >> activate2

		when:
		jobQueue << job
		service.doInit()

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
					(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson(
							new ZeebeLinkedResources([
									new ZeebeLinkedResources.ZeebeLinkedResource(
											"this_script",
											ZeebeBindingType.latest,
											"RPA",
											"?",
											ZeebeJobService.MAIN_SCRIPT_LINK_NAME,
											"this_script_latest"),

									*additionalResources
							])),
					
					*: additionalHeaders
			]

			getVariablesAsMap() >> variables
			getKey() >> 123
			getBpmnProcessId() >> "234"
			getProcessInstanceKey() >> 345
			getRetries() >> 3
		}
	}
}
