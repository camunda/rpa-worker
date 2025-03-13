package io.camunda.rpa.worker.zeebe

import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent

@Slf4j
class ZeebeE2ESpec extends AbstractE2ESpec {
	
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
	
	void "Provides input variables to script, and submits output variables to Zeebe"() {
		given:
		deployScript('input_and_output_variables', '''\
*** Settings ***
Library    Camunda

*** Tasks ***
Assert input variable
    Should Be Equal    ${expectedInputVariable}    expected-input-variable-value

Set an output variable
    Set Output Variable     anOutputVariable      output-variable-value
''')
		and:
		deploySimpleRobotProcess('input_and_output_variables_on_default', 'input_and_output_variables')

		when:
		ProcessInstanceEvent pinstance = createInstance("input_and_output_variables_on_default", 
			expectedInputVariable: "expected-input-variable-value")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
		spec.expectVariables(pinstance.processInstanceKey) {
			anOutputVariable == "output-variable-value"
		}
	}
	
	void "Reports Robot task failures"() {
		given:
		deployScript('task_failure', '''\
*** Tasks ***
Tasks
    Should Be Equal    one    two
''')
		and:
		deploySimpleRobotProcess('task_failure_on_default', 'task_failure')

		when:
		ProcessInstanceEvent pinstance = createInstance("task_failure_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.JOB_NO_RETRIES
				message().startsWith("There were task failures")
				message().contains("1 task, 0 passed, 1 failed")
			}
		}
	}

	void "Reports Robot errors"() {
		given:
		deployScript('task_error', '''\
*** Nothing ***
Nothing
''')
		and:
		deploySimpleRobotProcess('task_error_on_default', 'task_error')

		when:
		ProcessInstanceEvent pinstance = createInstance("task_error_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.JOB_NO_RETRIES
				message().startsWith("There were task errors")
				message().contains("Suite 'Main' contains no tests or tasks")
			}
		}
	}
	
	void "Reports Robot timeouts"() {
		given:
		deployScript('task_timeout', '''\
*** Tasks ***
Tasks
	Sleep    8s
''')
		and:
		deployProcess("task_timeout_on_default") // Timeout is 5s

		when:
		ProcessInstanceEvent pinstance = createInstance("task_timeout_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.JOB_NO_RETRIES
				message().startsWith("The execution timed out")
				message().contains("Main")
			}
		}
	}
	
	void "Runs the RPA Challenge"() {
		given:
		deployScriptFile("rpa_challenge")
		
		and:
		deploySimpleRobotProcess("rpa_challenge_on_default", "rpa_challenge")

		when:
		ProcessInstanceEvent pinstance = createInstance("rpa_challenge_on_default")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
		spec.expectVariables(pinstance.processInstanceKey) {
			resultText.toString().contains("100%")
		}
	}
}
	
