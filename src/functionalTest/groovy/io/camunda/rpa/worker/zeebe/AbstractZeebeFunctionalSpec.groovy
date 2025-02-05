package io.camunda.rpa.worker.zeebe

import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.client.api.worker.JobClient
import io.camunda.zeebe.client.api.worker.JobHandler
import io.camunda.zeebe.client.api.worker.JobWorker
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import org.spockframework.spring.SpringBean
import org.spockframework.spring.SpringSpy
import org.springframework.beans.factory.annotation.Autowired

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractZeebeFunctionalSpec extends AbstractFunctionalSpec {

	static final String TASK_PREFIX = "camunda::RPA-Task::"
	
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

	@SpringSpy
	WorkspaceCleanupService workspaceCleanupService

	JobClient jobClient = Mock()
	
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
