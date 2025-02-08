package io.camunda.rpa.worker.zeebe

import groovy.json.JsonOutput
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.files.api.StoreFilesRequest
import io.camunda.rpa.worker.util.IterableMultiPart
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1
import io.camunda.zeebe.client.api.command.FailJobCommandStep1
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobWorker
import okhttp3.MediaType
import okhttp3.MultipartReader
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono
import spock.lang.Tag

import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ZeebeFunctionalSpec extends AbstractZeebeFunctionalSpec {

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
	static final Closure<String> COMPANION_ROBOT_SCRIPT_TEMPLATE = { id -> """\
*** Settings ***
Library             Camunda
Library             OperatingSystem

*** Tasks ***
The tasks
	Create File    ${id}.txt    ${id}
    Set Output Variable     anOutputVariableFrom_${id}      output-value-from-companion
    Set Output Variable     anOutputVariableSameName       output-value-from-companion-${id}
"""
	}
	
	static final Closure<String> SLOW_ROBOT_SCRIPT_TEMPLATE = { time ->
		"""\
*** Tasks ***
The tasks
    Sleep    ${time}s
"""
	}
	
	static final String DO_NOTHING_SCRIPT = '''\
*** Tasks ***
Do Nothing
	No Operation
'''
	
	@Autowired
	Environment environment

	CountDownLatch handlerDidFinish = new CountDownLatch(1)

	@Override
	Map<String, String> getScripts() {
		return [
		        "existing_1": SAMPLE_ROBOT_SCRIPT,
				"erroring_1": ERRORING_ROBOT_SCRIPT,
				"companion_1": COMPANION_ROBOT_SCRIPT_TEMPLATE("pre0"),
		        "companion_2": COMPANION_ROBOT_SCRIPT_TEMPLATE("pre1"),
		        "companion_3": COMPANION_ROBOT_SCRIPT_TEMPLATE("post0"),
		        "companion_4": COMPANION_ROBOT_SCRIPT_TEMPLATE("post1"),
				"slow_15s": SLOW_ROBOT_SCRIPT_TEMPLATE(15),
		        "slow_8s": SLOW_ROBOT_SCRIPT_TEMPLATE(8),
				"env_check": ENV_CHECK_ROBOT_SCRIPT,
				"do_nothing": DO_NOTHING_SCRIPT
		]
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
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)
		
		then:
		1 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(environment.getRequiredProperty("camunda.rpa.robot.default-timeout", Duration)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}
		
		and:
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
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newThrowErrorCommand(_) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_TASKFAIL") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	void "Runs Robot task from Zeebe, passing in input variables, reports Robot error"() {
		given:
		service.doInit()
		withNoSecrets()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "erroring_1"))
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newThrowErrorCommand(_) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_ERROR") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	void "Reports system error when couldn't run job"() {
		given:
		service.doInit()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "fake_script"))
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(_) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("Script not found") }) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}
	
	void "Runs pre and post scripts, in order, and aggregates results"() {
		given:
		service.doInit()
		withSimpleSecrets([TEST_SECRET_KEY: 'TEST_SECRET_VALUE'])
		
		and:
		ActivatedJob jobWithPreAndPostScripts = anRpaJobWithPreAndPostScripts(
				["companion_1", "companion_2"],
				"existing_1",
				["companion_3", "companion_4"], 
				[anInputVariable: 'input-variable-value'])

		when:
		theJobHandler.handle(jobClient, jobWithPreAndPostScripts)
		handlerDidFinish.awaitRequired(10, TimeUnit.SECONDS)

		then:
		5 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			5 * updateTimeout(environment.getRequiredProperty("camunda.rpa.robot.default-timeout", Duration)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				5 * send()
			}
		}
		
		and:
		1 * jobClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			1 * variables([
					anOutputVariableFrom_pre0: "output-value-from-companion",
					anOutputVariableSameName: "output-value-from-companion-post1",
					anOutputVariableFrom_pre1: "output-value-from-companion",
					anOutputVariable: "output-variable-value",
					anOutputVariableFrom_post0: "output-value-from-companion",
					anOutputVariableFrom_post1: "output-value-from-companion",
			]) >> it
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}
	}

	@Tag("slow")
	void "Applies default timeout to jobs, aborts when exceeded, reports correct error"() {
		given:
		service.doInit()
		withNoSecrets()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "slow_15s"))
		handlerDidFinish.awaitRequired(14, TimeUnit.SECONDS)

		then:
		1 * jobClient.newThrowErrorCommand(_) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_TIMEOUT") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	@Tag("slow")
	void "Applies custom timeout to jobs, aborts when exceeded, reports correct error"() {
		given:
		service.doInit()
		withNoSecrets()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "slow_8s", [(ZeebeJobService.TIMEOUT_HEADER_NAME): "PT3S"]))
		handlerDidFinish.awaitRequired(7, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(Duration.ofSeconds(3)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}
		
		and:
		1 * jobClient.newThrowErrorCommand(_) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ROBOT_TIMEOUT") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage(_) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	void "Cleans up workspace after running job"() {
		given:
		service.doInit()
		withSimpleSecrets([TEST_SECRET_KEY: 'TEST_SECRET_VALUE'])
		CountDownLatch handlersDidFinish = new CountDownLatch(2)
		
		and:
		Queue<Workspace> workspaces = new LinkedList<>()
		workspaceCleanupService.deleteWorkspace(_) >> { Workspace p ->
			workspaces.add(p)
			Mono<Void> r = callRealMethod()
			r.block()
			handlersDidFinish.countDown()
			return r
		}
		
		and:
		jobClient.newCompleteCommand(_ as ActivatedJob) >> Stub(CompleteJobCommandStep1) {
			variables(_) >> it
			send() >> {
				handlersDidFinish.countDown()
				return null
			}
		}

		when:
		theJobHandler.handle(jobClient, anRpaJob([anInputVariable: 'input-variable-value']))
		handlersDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then: "Workspace deleted immediately"
		workspaces.size() == 1
		Files.notExists(workspaces.remove().path())
	}

	static final String ENV_CHECK_ROBOT_SCRIPT = '''\
*** Tasks ***
Assert input variable
    Should Not Be Empty    %{RPA_WORKSPACE_ID}
    Should Not Be Empty    %{RPA_WORKSPACE}
    Should Not Be Empty    %{RPA_SCRIPT}
    Should Not Be Empty    %{RPA_EXECUTION_KEY}
    Should Not Be Empty    %{RPA_ZEEBE_JOB_KEY}
    Should Not Be Empty    %{RPA_ZEEBE_BPMN_PROCESS_ID}
    Should Not Be Empty    %{RPA_ZEEBE_PROCESS_INSTANCE_KEY}
'''

	void "All built-in environment variables are available to job"() {
		given:
		service.doInit()
		withNoSecrets()

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "env_check"))
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * jobClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			variables(_) >> it
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}
	}

	void "Request to store files during Zeebe job triggers upload of workspace files to Zeebe with job metadata"() {
		given:
		service.doInit()
		bypassZeebeAuth()
		withNoSecrets()
		Workspace theWorkspace
		workspaceCleanupService.deleteWorkspace(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}

		and:
		zeebeDocuments.setDispatcher { rr ->

			new MockResponse().tap {
				setResponseCode(201)
				setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				setBody(new JsonOutput().toJson([
						'camunda.document.type': 'camunda',
						storeId                : 'the-store',
						documentId             : 'document-id',
						contentHash            : 'content-hash',
						metadata               : [fileName: 'filename']
				]))
			}
		}
		
		and:
		jobClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			variables(_) >> it
			send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}

		when:
		theJobHandler.handle(jobClient, anRpaJob([:], "do_nothing"))
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		and:
		block post()
				.uri("/file/store/${theWorkspace.path().fileName.toString()}")
				.body(BodyInserters.fromValue(new StoreFilesRequest("*.robot")))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, ZeebeDocumentDescriptor>>() {})

		then:
		with(zeebeDocuments.takeRequest(1, TimeUnit.SECONDS)) { req ->
			MultipartReader mpr = new MultipartReader(ResponseBody.create(
					req.body.readUtf8(),
					MediaType.parse(req.headers.get("Content-Type"))))

			Map<String, FormFieldPart> parts = new IterableMultiPart(mpr).collectEntries {
				[it.name(), it]
			}

			ZeebeDocumentDescriptor.Metadata metadata = objectMapper.readValue(parts.metadata.value(), ZeebeDocumentDescriptor.Metadata)
			metadata.processDefinitionId() == "123"
			metadata.processInstanceKey() == 234
		}
	}
}
