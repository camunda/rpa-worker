package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.script.api.DeployScriptRequest
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent

class ZeebeE2ESpec extends AbstractE2ESpec {

	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [CAMUNDA_RPA_SCRIPTS_SOURCE: 'local']
	}

	void "WIP: Zeebe Round Trip"() {
		when:
		rpaWorkerClient.deployScript(new DeployScriptRequest("script_1", '''\
*** Tasks ***
Do Nothing
	No Operation
'''))
		and:
		zeebeClient.newDeployResourceCommand()
				.addResourceFromClasspath("script_1_on_default.bpmn")
				.send()
				.get()

		and:
		ProcessInstanceEvent get = zeebeClient.newCreateInstanceCommand()
				.bpmnProcessId("script_1_on_default")
				.latestVersion()
				.send()
				.get()
		
		
		then:
		println get
	}
	
}
