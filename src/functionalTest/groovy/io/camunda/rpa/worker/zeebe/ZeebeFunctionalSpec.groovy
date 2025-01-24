package io.camunda.rpa.worker.zeebe

import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
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
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ZeebeFunctionalSpec extends AbstractFunctionalSpec {

	static final String TASK_PREFIX = "camunda::RPA-Task::"
	static final String SAMPLE_ROBOT_SCRIPT = '''\
*** Settings ***
Library             Camunda

*** Tasks ***
Assert input variable
    Should Be Equal    ${anInputVariable}    input-variable-value
    Should Be Equal    %{SECRET_TEST_SECRET_KEY}    TEST_SECRET_VALUE

Set an output variable
    Set Output Variable     anOutputVariable      output-variable-value
'''
	
	static final String ERRORING_ROBOT_SCRIPT = '''\
*** Nothing ***
Nothing
'''

	@Autowired
	ZeebeJobService service

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
	
	@SpringBean
	ZeebeClient zeebeClient = Stub() {
		newWorker() >> builder1
	}

	JobClient jobClient = Mock()
	CountDownLatch handlerDidFinish = new CountDownLatch(1)
	
	void setupSpec() {
		Path scriptsDir = Paths.get("scripts_ftest")
		if(Files.exists(scriptsDir))
			Files.walk(scriptsDir).filter(Files::isRegularFile).forEach(Files::delete)
		
		Files.createDirectories(scriptsDir)
		scriptsDir.resolve("existing_1.robot").text = SAMPLE_ROBOT_SCRIPT
		scriptsDir.resolve("erroring_1.robot").text = ERRORING_ROBOT_SCRIPT
	}
	
	void "Subscribes on init"() {
		when:
		service.doInit()
		
		then:
		1 * builder1.jobType(TASK_PREFIX + "default") >> builder2
		1 * builder3.open() >> Stub(JobWorker)
	}
	
	void "Runs Robot task from Zeebe, passing in input variables and secrets, reports success with output variables"() {
		given:
		service.doInit()
		withSimpleSecrets([TEST_SECRET_KEY: 'TEST_SECRET_VALUE'])

		when:
		theJobHandler.handle(jobClient, anRpaJob([anInputVariable: 'input-variable-value']))
		handlerDidFinish.await(2, TimeUnit.SECONDS)
		
		then:
		1 * jobClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			1 * variables([anOutputVariable: 'output-variable-value']) >> it
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}
	}

	void "Runs Robot task from Zeebe, passing in input variables, reports fail"() {
		given:
		service.doInit()
		withNoSecrets()

		when:
		theJobHandler.handle(jobClient, anRpaJob([anInputVariable: 'UNEXPECTED-input-variable-value']))
		handlerDidFinish.await(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newThrowErrorCommand(_) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_TASKFAIL") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}

	void "Runs Robot task from Zeebe, passing in input variables, reports Robot error"() {
		given:
		service.doInit()
		withNoSecrets()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "erroring_1"))
		handlerDidFinish.await(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newThrowErrorCommand(_) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_ERROR") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send()
			}
		}
	}

	void "Reports system error when couldn't run job"() {
		given:
		service.doInit()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "fake_script"))
		handlerDidFinish.await(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(_) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("Script not found") }) >> it
				1 * send()
			}
		}
	}

	private ActivatedJob anRpaJob(Map<String, Object> variables = [:], String scriptKey = "existing_1") {
		return Stub(ActivatedJob) {
			getCustomHeaders() >> [(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson(
					new ZeebeLinkedResources([
							new ZeebeLinkedResources.ZeebeLinkedResource(
									scriptKey,
									ZeebeBindingType.latest,
									"RPA",
									"?",
									"RPAScript",
									scriptKey)
					])
			)]

			getVariablesAsMap() >> variables
		}
	}

}
