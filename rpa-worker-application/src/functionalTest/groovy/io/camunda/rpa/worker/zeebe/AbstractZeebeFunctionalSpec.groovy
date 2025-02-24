package io.camunda.rpa.worker.zeebe

import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.ZeebeFuture
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1
import io.camunda.zeebe.client.api.command.FinalCommandStep
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivateJobsResponse
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import org.spockframework.spring.SpringBean
import org.spockframework.spring.SpringSpy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.annotation.DirtiesContext

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
abstract class AbstractZeebeFunctionalSpec extends AbstractFunctionalSpec {

	static final String TASK_PREFIX = "camunda::RPA-Task::"
	
	BlockingQueue<ActivatedJob> jobQueue = new LinkedBlockingQueue<>()
	
	@SpringBean
	ZeebeClient zeebeClient = Mock(ZeebeClient) {
		newUpdateJobCommand(_) >> Stub(UpdateJobCommandStep1) {
			updateTimeout(_) >> Stub(UpdateJobCommandStep1.UpdateJobCommandStep2)
		}

		newActivateJobsCommand() >> Stub(ActivateJobsCommandStep1) {
			jobType(_) >> Stub(ActivateJobsCommandStep1.ActivateJobsCommandStep2) {
				maxJobsToActivate(_) >> Stub(ActivateJobsCommandStep1.ActivateJobsCommandStep3) {
					requestTimeout(_) >> Stub(FinalCommandStep) {
						send() >> Stub(ZeebeFuture) {
							handle(_) >> { BiFunction fn -> 
								CompletableFuture.supplyAsync {
									ActivatedJob job = jobQueue.poll(2000, TimeUnit.MILLISECONDS)
									return Stub(ActivateJobsResponse) {
										getJobs() >> { job ? [job] : [] }
									}
								}.handle(fn)
							}
						}
					}
				}
			}
		}
	}

	@SpringSpy
	WorkspaceCleanupService workspaceCleanupService
	
	@Autowired
	ApplicationEventPublisher eventPublisher

	abstract Map<String, String> getScripts()

	void setupSpec() {
		Path scriptsDir = Paths.get("scripts_ftest")
		if (Files.exists(scriptsDir))
			Files.walk(scriptsDir).filter(Files::isRegularFile).forEach(Files::delete)

		Files.createDirectories(scriptsDir)
		
		scripts.each { k, v ->
			scriptsDir.resolve("${k}.robot").text = v
		}
	}
	
	void setup() {
		eventPublisher.publishEvent(new ZeebeReadyEvent(zeebeClient))
	}

	protected ActivatedJob anRpaJob(Map<String, Object> variables = [:], String scriptKey = "existing_1", Map additionalHeaders = [:], int jobNum = 0) {
		return Stub(ActivatedJob) {
			getCustomHeaders() >> [
					(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson(
							new ZeebeLinkedResources([
									new ZeebeLinkedResources.ZeebeLinkedResource(
											scriptKey,
											ZeebeBindingType.latest,
											"RPA",
											"?",
											ZeebeJobService.MAIN_SCRIPT_LINK_NAME,
											scriptKey)
							])),

					*: additionalHeaders
			]
			
			getKey() >> { jobNum }
			getVariablesAsMap() >> variables
			getBpmnProcessId() >> "123"
			getProcessInstanceKey() >> 234
			getRetries() >> 3
		}
	}

	protected ActivatedJob anRpaJobWithPreAndPostScripts(
			List<String> preScripts,
			String mainScript,
			List<String> postScripts,
			Map<String, Object> inputVariables = [:]) {

		return Stub(ActivatedJob) {
			getCustomHeaders() >> [(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson(
					new ZeebeLinkedResources(
							preScripts.collect { s ->
								new ZeebeLinkedResources.ZeebeLinkedResource(
										s,
										ZeebeBindingType.latest,
										"RPA",
										"?",
										ZeebeJobService.BEFORE_SCRIPT_LINK_NAME,
										s)
							}
									+
									[
											new ZeebeLinkedResources.ZeebeLinkedResource(
													mainScript,
													ZeebeBindingType.latest,
													"RPA",
													"?",
													ZeebeJobService.MAIN_SCRIPT_LINK_NAME,
													mainScript)
									]
									+
									postScripts.collect { s ->
										new ZeebeLinkedResources.ZeebeLinkedResource(
												s,
												ZeebeBindingType.latest,
												"RPA",
												"?",
												ZeebeJobService.AFTER_SCRIPT_LINK_NAME,
												s)
									}))]

			getVariablesAsMap() >> inputVariables
		}
	}
}
