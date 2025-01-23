package io.camunda.rpa.worker.zeebe

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.ExecutionResult
import io.camunda.rpa.worker.robot.ExecutionResult.Result
import io.camunda.rpa.worker.robot.RobotService
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.script.ScriptRepository
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1
import io.camunda.zeebe.client.api.command.FailJobCommandStep1
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.api.worker.JobHandler
import io.camunda.zeebe.client.api.worker.JobWorker
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ZeebeJobServiceSpec extends Specification implements PublisherUtils {

	static final String TASK_PREFIX = "camunda::RPA-Task::"

	JobWorkerBuilderStep1 builder1 = Mock() {
		jobType(_) >> { builder2 }
	}
	JobHandler theJobHandler
	JobWorkerBuilderStep1.JobWorkerBuilderStep2 builder2 = Stub() {
		handler(_) >> { JobHandler jh ->
			theJobHandler = jh
			return builder3
		}
	}
	JobWorkerBuilderStep1.JobWorkerBuilderStep3 builder3 = Mock() {
		open() >> Stub(JobWorker)
	}
	ZeebeClient zeebeClient = Mock() {
		newWorker() >> builder1
	}
	
	ZeebeProperties zeebeProperties = new ZeebeProperties(TASK_PREFIX, ["tag-one", "tag-two"].toSet())
	RobotService robotService = Mock()
//	SecretsService secretsService = Stub() {
//		getSecrets() >> Mono.just([secretVar: 'secret-value'])
//	}
	RobotScript script = new RobotScript("this_script", null)
	ScriptRepository scriptRepository = Stub() {
		findById("this_script_latest") >> Mono.just(script)
	}
	ObjectMapper objectMapper = new ObjectMapper()

	@Subject
	ZeebeJobService service = new ZeebeJobService(
			zeebeClient,
			zeebeProperties,
			robotService,
			scriptRepository,
			objectMapper)

	JobClient jobClient = Mock()

	void "Subscribes to correct tasks on init"() {
		when:
		service.doInit()

		then:
		1 * builder1.jobType(TASK_PREFIX + "tag-one") >> builder2
		1 * builder3.open() >> Stub(JobWorker)
		
		then:
		1 * builder1.jobType(TASK_PREFIX + "tag-two") >> builder2
		1 * builder3.open() >> Stub(JobWorker)
	}

	void "Runs received task and reports success"() {
		given:
		ActivatedJob job = anRpaJob()
		service.doInit()

		when:
		theJobHandler.handle(jobClient, job)

		then:
		1 * robotService.execute(script, _, _) >> Mono.just(new ExecutionResult(Result.PASS, "", [outputVar: 'output-var-value']))

		and:
		1 * jobClient.newCompleteCommand(job) >> Mock(CompleteJobCommandStep1) {
			1 * variables([outputVar: 'output-var-value']) >> it
			1 * send()
		}
	}

	void "Runs received task and reports Robot failure/error"(Result result, String expectedCode) {
		given:
		ActivatedJob job = anRpaJob()
		service.doInit()

		when:
		theJobHandler.handle(jobClient, job)

		then:
		1 * robotService.execute(script, _, _) >> Mono.just(new ExecutionResult(result, "", [outputVar: 'output-var-value']))

		and:
		1 * jobClient.newThrowErrorCommand(job) >> Mock(ThrowErrorCommandStep1) {
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
		service.doInit()

		when:
		theJobHandler.handle(jobClient, job)

		then:
		1 * robotService.execute(script, _, _) >> Mono.error(new RuntimeException("Bang!"))

		and:
		1 * jobClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(_) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}

	void "Passes variables to Robot execution"() {
		given:
		service.doInit()

		when: "There are specific RPA Input Variables available"
		ActivatedJob job1 = anRpaJob([
				camundaRpaTaskInput: [rpaVar: 'the-value'], otherVar: 'should-not-be-used'])
		theJobHandler.handle(jobClient, job1)

		then: "The RPA Input Variables are passed to the Robot execution"
//		1 * robotService.execute(script, [rpaVar: 'the-value'], [SECRET_SECRETVAR: 'secret-value']) >> Mono.empty()
		1 * robotService.execute(script, [rpaVar: 'the-value'], [:]) >> Mono.empty()

		when: "There are NO specific RPA Input Variables available"
		ActivatedJob job2 = anRpaJob([otherVar: 'other-val'])
		theJobHandler.handle(jobClient, job2)

		then: "The Job's main variables are passed to the Robot execution"
//		1 * robotService.execute(script, [otherVar: 'other-val'], [SECRET_SECRETVAR: 'secret-value']) >> Mono.empty()
		1 * robotService.execute(script, [otherVar: 'other-val'], [:]) >> Mono.empty()
	}
	
	void "Errors when can't find script in headers"() {
		given:
		RobotScript script = new RobotScript("this_script", null)
		scriptRepository.findById("this_script_latest") >> Mono.just(script)

		and:
		JobClient jobClient = Mock()
		ActivatedJob job = Stub()
		builder1.jobType(_) >> builder2
		builder3.open() >> Stub(JobWorker)

		and:
		service.doInit()

		when:
		theJobHandler.handle(jobClient, job)

		then:
		1 * jobClient.newFailCommand(job) >> Mock(FailJobCommandStep1) {
			1 * retries(_) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}

	private ActivatedJob anRpaJob(Map<String, Object> variables = [:]) {
		return Stub(ActivatedJob) {
			getCustomHeaders() >> [(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson(
					new ZeebeLinkedResources([
							new ZeebeLinkedResources.ZeebeLinkedResource(
									"this_script",
									ZeebeBindingType.latest,
									"RPA",
									"?",
									"RPAScript",
									"this_script_latest")
					])
			)]
			
			getVariablesAsMap() >> variables
		}
	}
}
