package io.camunda.rpa.worker.zeebe

import groovy.json.JsonOutput
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.files.api.StoreFilesRequest
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.util.IterableMultiPart
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1
import io.camunda.zeebe.client.api.command.FailJobCommandStep1
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl
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
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ZeebeFunctionalSpec extends AbstractScriptRepositoryProvidingZeebeFunctionalSpec {

	static final Map<String, String> SOME_SIMPLE_SECRETS = [
			TEST_SECRET_KEY: 'TEST_SECRET_VALUE',
			'Test-Secret-Key-2': 'TEST_SECRET_VALUE_2'
	]
	
	static final String SAMPLE_ROBOT_SCRIPT = '''\
*** Settings ***
Library             Camunda

*** Tasks ***
Assert input variable
    Should Be Equal    ${anInputVariable}    input-variable-value
    Should Be Equal    ${SECRETS.TEST_SECRET_KEY}    TEST_SECRET_VALUE
    Should Be Equal    ${SECRETS['Test-Secret-Key-2']}    TEST_SECRET_VALUE_2

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
	
	static final String CHECK_ADDITIONAL_FILES_SCRIPT = '''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Check
	${fileContents1}    Get File    one.resource
	${fileContents2}    Get File    two/three.resource
	
	Should Be Equal    ${fileContents1}    one.resource contents
	Should Be Equal    ${fileContents2}    three.resource contents
'''
	
	@Autowired
	Environment environment

	CountDownLatch handlerDidFinish = new CountDownLatch(1)

	@Override
	List<RobotScript> getScripts() {
		return [
		        RobotScript.builder().id("existing_1").body(SAMPLE_ROBOT_SCRIPT).build(),
		        RobotScript.builder().id("erroring_1").body(ERRORING_ROBOT_SCRIPT).build(),
		        RobotScript.builder().id("companion_1").body(COMPANION_ROBOT_SCRIPT_TEMPLATE("pre0")).build(),
		        RobotScript.builder().id("companion_2").body(COMPANION_ROBOT_SCRIPT_TEMPLATE("pre1")).build(),
		        RobotScript.builder().id("companion_3").body(COMPANION_ROBOT_SCRIPT_TEMPLATE("post0")).build(),
		        RobotScript.builder().id("companion_4").body(COMPANION_ROBOT_SCRIPT_TEMPLATE("post1")).build(),
		        RobotScript.builder().id("slow_15s").body(SLOW_ROBOT_SCRIPT_TEMPLATE(15)).build(),
		        RobotScript.builder().id("slow_8s").body(SLOW_ROBOT_SCRIPT_TEMPLATE(8)).build(),
		        RobotScript.builder().id("env_check").body(ENV_CHECK_ROBOT_SCRIPT).build(),
		        RobotScript.builder().id("do_nothing").body(DO_NOTHING_SCRIPT).build(),
		        RobotScript.builder()
				        .id("has_additional_files")
				        .body(CHECK_ADDITIONAL_FILES_SCRIPT)
				        .file(Paths.get("one.resource"), "one.resource contents".bytes)
				        .file(Paths.get("two/three.resource"), "three.resource contents".bytes)
				        .build(),
		]
	}

	void "Runs Robot task from Zeebe, passing in input variables and secrets, reports success with output variables"() {
		given:
		withSimpleSecrets(SOME_SIMPLE_SECRETS)

		when:
		zeebeJobService.handleJob(anRpaJob([anInputVariable: 'input-variable-value'])).subscribe()
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)
		
		then:
		1 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(environment.getRequiredProperty("camunda.rpa.robot.default-timeout", Duration)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}
		
		and:
		1 * zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}

		and:
		1 * zeebeClient.newSetVariablesCommand(_) >> Mock(SetVariablesCommandStep1) {
			1 * variables([anOutputVariable: 'output-variable-value']) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}
	}

	void "Runs Robot task from Zeebe, passing in input variables, reports fail"() {
		given:
		withNoSecrets()

		when:
		zeebeJobService.handleJob(anRpaJob([anInputVariable: 'UNEXPECTED-input-variable-value'])).subscribe()
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newSetVariablesCommand(_) >> Mock(SetVariablesCommandStep1) {
			1 * variables([:]) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}

		and:
		1 * zeebeClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(2) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("There were task failures") }) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	void "Runs Robot task from Zeebe, passing in input variables, reports Robot error"() {
		given:
		withNoSecrets()

		when:
		zeebeJobService.handleJob(anRpaJob([:], "erroring_1")).subscribe()
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newSetVariablesCommand(_) >> Mock(SetVariablesCommandStep1) {
			1 * variables([:]) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}
		
		and:
		1 * zeebeClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(2) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("There were task errors") }) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	void "Reports system error when couldn't run job"() {
		when:
		zeebeJobService.handleJob(anRpaJob([:], "fake_script")).subscribe()
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(2) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("Script not found") }) >> it
				_ * variables(_) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}
	
	void "Runs pre and post scripts, in order, and aggregates results"() {
		given:
		withSimpleSecrets(SOME_SIMPLE_SECRETS)
		
		and:
		ActivatedJob jobWithPreAndPostScripts = anRpaJobWithPreAndPostScripts(
				["companion_1", "companion_2"],
				"existing_1",
				["companion_3", "companion_4"], 
				[anInputVariable: 'input-variable-value'])

		when:
		zeebeJobService.handleJob(jobWithPreAndPostScripts).subscribe()
		handlerDidFinish.awaitRequired(10, TimeUnit.SECONDS)

		then:
		5 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			5 * updateTimeout(environment.getRequiredProperty("camunda.rpa.robot.default-timeout", Duration)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				5 * send()
			}
		}
		
		and:
		1 * zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}
		
		and:
		1 * zeebeClient.newSetVariablesCommand(jobWithPreAndPostScripts.processInstanceKey) >> Mock(SetVariablesCommandStep1) {
			1 * variables([
					anOutputVariableFrom_pre0: "output-value-from-companion",
					anOutputVariableSameName: "output-value-from-companion-post1",
					anOutputVariableFrom_pre1: "output-value-from-companion",
					anOutputVariable: "output-variable-value",
					anOutputVariableFrom_post0: "output-value-from-companion",
					anOutputVariableFrom_post1: "output-value-from-companion",
			]) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}
	}

	@Tag("slow")
	void "Applies default timeout to jobs, aborts when exceeded, reports correct error"() {
		given:
		withNoSecrets()

		when:
		zeebeJobService.handleJob(anRpaJob([:], "slow_15s")).subscribe()
		handlerDidFinish.awaitRequired(14, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(2) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("The execution timed out") }) >> it
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
		withNoSecrets()

		when:
		zeebeJobService.handleJob(anRpaJob([:], "slow_8s", [(ZeebeJobService.TIMEOUT_HEADER_NAME): "PT3S"])).subscribe()
		handlerDidFinish.awaitRequired(7, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(Duration.ofSeconds(3)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}
		
		and:
		1 * zeebeClient.newFailCommand(_) >> Mock(FailJobCommandStep1) {
			1 * retries(2) >> Mock(FailJobCommandStep1.FailJobCommandStep2) {
				1 * errorMessage({ it.contains("The execution timed out") }) >> it
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}
		}
	}

	void "Cleans up workspace after running job"() {
		given:
		withSimpleSecrets(SOME_SIMPLE_SECRETS)
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
		zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Stub(CompleteJobCommandStep1) {
			send() >> {
				handlersDidFinish.countDown()
				return null
			}
		}

		when:
		zeebeJobService.handleJob(anRpaJob([anInputVariable: 'input-variable-value'])).subscribe()
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
		withNoSecrets()

		when:
		zeebeJobService.handleJob(anRpaJob([:], "env_check")).subscribe()
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}
	}

	void "Request to store files during Zeebe job triggers upload of workspace files to Zeebe with job metadata"() {
		given:
		bypassZeebeAuth()
		withNoSecrets()
		CountDownLatch handlersDidFinish = new CountDownLatch(2)
		
		and:
		Workspace theWorkspace
		workspaceCleanupService.deleteWorkspace(_) >> { Workspace w ->
			theWorkspace = w
			handlersDidFinish.countDown()
			return Mono.empty()
		}

		and:
		zeebeApi.setDispatcher { rr ->

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
		zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			send() >> {
				handlersDidFinish.countDown()
				return null
			}
		}

		when:
		zeebeJobService.handleJob(anRpaJob([:], "do_nothing")).subscribe()
		handlersDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		and:
		block post()
				.uri("/file/store/${theWorkspace.path().fileName.toString()}")
				.body(BodyInserters.fromValue(new StoreFilesRequest("*.robot")))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, ZeebeDocumentDescriptor>>() {})

		then:
		with(zeebeApi.takeRequest(1, TimeUnit.SECONDS)) { req ->
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
	
	void "Makes additional files available in the Workspace"() {
		given:
		withNoSecrets()

		when:
		zeebeJobService.handleJob(anRpaJob([:], "has_additional_files")).subscribe()
		handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

		then:
		1 * zeebeClient.newUpdateJobCommand(_ as ActivatedJob) >> Mock(UpdateJobCommandStep1) {
			1 * updateTimeout(environment.getRequiredProperty("camunda.rpa.robot.default-timeout", Duration)) >> Mock(UpdateJobCommandStep1.UpdateJobCommandStep2) {
				1 * send()
			}
		}

		and:
		1 * zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
			1 * send() >> {
				handlerDidFinish.countDown()
				return null
			}
		}

		and:
		1 * zeebeClient.newSetVariablesCommand(_) >> Mock(SetVariablesCommandStep1) {
			1 * variables([:]) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
				1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
			}
		}
	}

	static class ZeebeScriptSourceFunctionalSpec extends AbstractZeebeFunctionalSpec  {
		
		CountDownLatch handlerDidFinish = new CountDownLatch(1)

		void "Runs Robot task from Zeebe, fetching script from Zeebe"() {
			given:
			withSimpleSecrets(SOME_SIMPLE_SECRETS)
			
			and:
			zeebeApi.enqueue(new MockResponse().tap {
				setHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.camunda.rpa+json")
				setResponseCode(200)
				setBody(JsonOutput.toJson([
						id                      : "existing_1",
						name                    : "Robot Script 1", 
						executionPlatform       : "Camunda Cloud", 
						executionPlatformVersion: "8.7.0", 
						script                  : SAMPLE_ROBOT_SCRIPT]))
			})

			when:
			zeebeJobService.handleJob(anRpaJob([anInputVariable: 'input-variable-value'])).subscribe()
			handlerDidFinish.awaitRequired(2, TimeUnit.SECONDS)

			then:
			with(zeebeApi.takeRequest(2, TimeUnit.SECONDS)) { req ->
				req.method == "GET"
				req.headers[HttpHeaders.AUTHORIZATION] == "Bearer the-access-token"
				URI.create(req.path).path == "/v2/resources/existing_1/content"
			}
			
			and:
			1 * zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
				1 * send() >> {
					handlerDidFinish.countDown()
					return null
				}
			}

			and:
			1 * zeebeClient.newSetVariablesCommand(_) >> Mock(SetVariablesCommandStep1) {
				1 * variables([anOutputVariable: 'output-variable-value']) >> Mock(SetVariablesCommandStep1.SetVariablesCommandStep2) {
					1 * send() >> new ZeebeClientFutureImpl<>().tap { complete(null) }
				}
			}
		}
	}
}
