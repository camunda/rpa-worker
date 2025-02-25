package io.camunda.rpa.worker.zeebe

import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent

@Slf4j
class ZeebeE2ESpec extends AbstractE2ESpec {
	
	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [CAMUNDA_RPA_SCRIPTS_SOURCE: "zeebe"]
	}

	void "Process errors with correct message when no linked resource providing main script"() {
		when:
		deployProcess("no_script_on_default")

		and:
		ProcessInstanceEvent pinstance = createInstance("no_script_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.JOB_NO_RETRIES
				message() == "Failed to find exactly 1 LinkedResource providing the main script"
			}
		}
	}

	void "Runs deployed script, and reports success"() {
		given:
		deployScript('noop', '''\
*** Tasks ***
Tasks
    No Operation
''')
		and:
		deploySimpleRobotProcess('noop_on_default', 'noop')

		when:
		ProcessInstanceEvent pinstance = createInstance("noop_on_default")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}
}
	
