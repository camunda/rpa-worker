package io.camunda.rpa.worker.zeebe

import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.UpdateJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType
import org.spockframework.spring.SpringBean
import org.spockframework.spring.SpringSpy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.annotation.DirtiesContext

@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
abstract class AbstractZeebeFunctionalSpec extends AbstractFunctionalSpec {

	@SpringBean
	ZeebeClient zeebeClient = Mock(ZeebeClient) {
		newUpdateJobCommand(_) >> Stub(UpdateJobCommandStep1) {
			updateTimeout(_) >> Stub(UpdateJobCommandStep1.UpdateJobCommandStep2)
		}
	}

	@SpringSpy
	WorkspaceCleanupService workspaceCleanupService
	
	@Autowired
	ApplicationEventPublisher eventPublisher
	
	@SpringBean
	ZeebeJobPoller zeebeJobPoller = Stub()
	
	@Autowired
	ZeebeJobService zeebeJobService

	protected ActivatedJob anRpaJob(Map<String, Object> variables = [:], String scriptKey = "existing_1", Map additionalHeaders = [:], int jobNum = 0) {
		return Stub(ActivatedJob) {
			getCustomHeaders() >> [
					(ZeebeJobService.LINKED_RESOURCES_HEADER_NAME): JsonOutput.toJson([
							new ZeebeLinkedResource(
									scriptKey,
									ZeebeBindingType.latest,
									"RPA",
									"?",
									ZeebeJobService.MAIN_SCRIPT_LINK_NAME,
									scriptKey)
					]),

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
					preScripts.collect { s ->
						new ZeebeLinkedResource(
								s,
								ZeebeBindingType.latest,
								"RPA",
								"?",
								ZeebeJobService.BEFORE_SCRIPT_LINK_NAME,
								s)
							}
							+
							[
									new ZeebeLinkedResource(
											mainScript,
											ZeebeBindingType.latest,
											"RPA",
											"?",
											ZeebeJobService.MAIN_SCRIPT_LINK_NAME,
											mainScript)
							]
							+
							postScripts.collect { s ->
								new ZeebeLinkedResource(
										s,
										ZeebeBindingType.latest,
										"RPA",
										"?",
										ZeebeJobService.AFTER_SCRIPT_LINK_NAME,
										s)
							})]

			getVariablesAsMap() >> inputVariables
}
	}
}
