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

	void "Runs before and after scripts"() {
		given:
		deployScript('before_and_after_scripts_good_before', '''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Tasks
    Create File    before.txt
''')
		deployScript('before_and_after_scripts_good_main', '''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Tasks
    File Should Exist    before.txt
    Create File    main.txt
''')
		deployScript('before_and_after_scripts_good_after', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Tasks
    File Should Exist    main.txt
    Set Output Variable    afterRan    ${True}
''')
		and:
		deployProcess('before_and_after_scripts_good_on_default')

		when:
		ProcessInstanceEvent pinstance = createInstance("before_and_after_scripts_good_on_default")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
		spec.expectVariables(pinstance.processInstanceKey) {
			afterRan
		}
	}

	void "Stops execution and returns correct results for pre/post script failure"() {
		given:
		['before_and_after_scripts_fail_before', 'before_and_after_scripts_fail_main', 'before_and_after_scripts_fail_after'].each { scriptName ->
			deployScript(scriptName, '''\
*** Tasks ***
Tasks
    Fail
''')
		}
		
		and:
		deployProcess('before_and_after_scripts_fail_on_default')

		when:
		ProcessInstanceEvent pinstance = createInstance("before_and_after_scripts_fail_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.JOB_NO_RETRIES
				message().startsWith("There were task failures")
				message().contains("pre_0")
				! message().contains("main")
				! message().contains("post_0")
			}
		}
	}

	void "Sends throw error commands to Zeebe from script"() {
		given:
		deployScript('throw_bpmn_error', '''\
*** Settings ***
Library    Camunda
Library    RequestsLibrary
Test Teardown    Run on teardown

*** Tasks ***
Throw error
    Set Output Variable    anOutputVariable    output-variable-value
    Throw BPMN Error    ERROR_CODE    Bad things happened

*** Keywords ***
Run on teardown
    Set Output Variable    teardownDidRun    ${True}
''')
		and:
		deploySimpleRobotProcess('throw_bpmn_error_on_default', 'throw_bpmn_error')

		when:
		ProcessInstanceEvent pinstance = createInstance("throw_bpmn_error_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.UNHANDLED_ERROR_EVENT
				message().contains("ERROR_CODE")
				message().contains("Bad things happened")
			}
		}
		spec.expectVariables(pinstance.processInstanceKey) {
			anOutputVariable == 'output-variable-value'
			teardownDidRun
		}
	}

	void "Sends throw error commands to Zeebe from script - no message"() {
		given:
		deployScript('throw_bpmn_error', '''\
*** Settings ***
Library    Camunda
Library    RequestsLibrary
Test Teardown    Run on teardown

*** Tasks ***
Throw error
    Set Output Variable    anOutputVariable    output-variable-value
    Throw BPMN Error    ERROR_CODE

*** Keywords ***
Run on teardown
    Set Output Variable    teardownDidRun    ${True}
''')
		and:
		deploySimpleRobotProcess('throw_bpmn_error_on_default', 'throw_bpmn_error')

		when:
		ProcessInstanceEvent pinstance = createInstance("throw_bpmn_error_on_default")

		then:
		spec.expectIncidents(pinstance.processInstanceKey) { incidents ->
			incidents.size() == 1
			with(incidents.first()) {
				type() == OperateClient.GetIncidentsResponse.Item.Type.UNHANDLED_ERROR_EVENT
				message().contains("ERROR_CODE")
			}
		}
		spec.expectVariables(pinstance.processInstanceKey) {
			anOutputVariable == 'output-variable-value'
			teardownDidRun
		}
	}

	void "Sends throw error commands to Zeebe from script - with variables"() {
		given:
		deployScript('throw_bpmn_error', '''\
*** Settings ***
Library    Camunda
Library    RequestsLibrary
Test Teardown    Run on teardown

*** Tasks ***
Throw error
    Set Output Variable    anOutputVariable    output-variable-value
    ${errorVars}=    Create Dictionary    errorVariable=error-variable-value
    Throw BPMN Error    ERROR_CODE    Bad things happened    ${errorVars}

*** Keywords ***
Run on teardown
    Set Output Variable    teardownDidRun    ${True}
''')

		and:
		deployScript('catch_bpmn_error', '''\
*** Settings ***
Library    Camunda

*** Tasks ***
Catch error
    Should Be Equal    ${errorVariable}    error-variable-value
    Set Output Variable    catchDidRun    ${True}
''')

		and:
		deployProcess("throw_and_catch_bpmn_error_on_default")

		when:
		ProcessInstanceEvent pinstance = createInstance("throw_and_catch_bpmn_error_on_default")

		then:
		expectNoIncident(pinstance.processInstanceKey)

		and:
		spec.expectVariables(pinstance.processInstanceKey) {
			anOutputVariable == 'output-variable-value'
			teardownDidRun
			catchDidRun
			errorVariable == 'error-variable-value'
		}
	}

	void "Sends throw error commands to Zeebe from script - with variables, no message"() {
		given:
		deployScript('throw_bpmn_error', '''\
*** Settings ***
Library    Camunda
Library    RequestsLibrary
Test Teardown    Run on teardown

*** Tasks ***
Throw error
    Set Output Variable    anOutputVariable    output-variable-value
    ${errorVars}=    Create Dictionary    errorVariable=error-variable-value
    Throw BPMN Error    ERROR_CODE    variables=${errorVars}

*** Keywords ***
Run on teardown
    Set Output Variable    teardownDidRun    ${True}
''')

		and:
		deployScript('catch_bpmn_error', '''\
*** Settings ***
Library    Camunda

*** Tasks ***
Catch error
    Should Be Equal    ${errorVariable}    error-variable-value
    Set Output Variable    catchDidRun    ${True}
''')

		and:
		deployProcess("throw_and_catch_bpmn_error_on_default")

		when:
		ProcessInstanceEvent pinstance = createInstance("throw_and_catch_bpmn_error_on_default")

		then:
		expectNoIncident(pinstance.processInstanceKey)

		and:
		spec.expectVariables(pinstance.processInstanceKey) {
			anOutputVariable == 'output-variable-value'
			teardownDidRun
			catchDidRun
			errorVariable == 'error-variable-value'
		}
	}

	void "Runs deployed script with additional files"() {
		given:
		deployScript('has_additional_files', '''\
*** Settings ***
Library   OperatingSystem

*** Tasks ***
Check
	${fileContents1}    Get File    one.resource
	${fileContents2}    Get File    two/three.resource
	
	Should Be Equal    ${fileContents1}    one.resource contents
	Should Be Equal    ${fileContents2}    three.resource contents
''', [
				'one.resource'      : 'one.resource contents',
				'two/three.resource': 'three.resource contents',
		])

		and:
		deploySimpleRobotProcess('has_additional_files_on_default', 'has_additional_files')

		when:
		ProcessInstanceEvent pinstance = createInstance("has_additional_files_on_default")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}
	
	void "Runs deployed script with additional files with existing workspace file"() {
		given:
		deployScript('extra_resources_with_before_before', '''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Tasks
    No Operation
''', ['one.resource': 'original one.resource contents'])
		
		and:
		deployScript('extra_resources_with_before_main', '''\
*** Settings ***
Library   OperatingSystem

*** Tasks ***
Check
	${fileContents1}    Get File    one.resource
	
	Should Be Equal    ${fileContents1}    replacement one.resource contents
''', ['one.resource'      : 'replacement one.resource contents'])

		and:
		deployProcess("extra_resources_with_before_on_default")

		when:
		ProcessInstanceEvent pinstance = createInstance("extra_resources_with_before_on_default")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}

	void "Runs deployed script with additional files - large RPA resource"() {
		given:
		deployResource("large_rpa_resource")

		and:
		deploySimpleRobotProcess('large_rpa_resource_on_default', 'large_rpa_resource')

		when:
		ProcessInstanceEvent pinstance = createInstance("large_rpa_resource_on_default")

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}
}
	
