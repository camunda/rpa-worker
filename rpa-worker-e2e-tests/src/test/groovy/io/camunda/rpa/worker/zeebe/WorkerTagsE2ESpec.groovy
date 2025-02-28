package io.camunda.rpa.worker.zeebe


import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent

@Slf4j
class WorkerTagsE2ESpec extends AbstractE2ESpec {
	
	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [CAMUNDA_RPA_ZEEBE_WORKERTAGS: "blue,red"]
	}

	void "Runs Zeebe jobs from correct worker tags"() {
		given:
		deployScript('noop', '''\
*** Tasks ***
Tasks
    No Operation
''')
		and:
		deploySimpleRobotProcess(
				'noop_on_blue', 
				'noop', 
				'blue')
		
		deploySimpleRobotProcess(
				'noop_on_green',
				'noop',
				'green')

		when:
		ProcessInstanceEvent pinstanceBlue = createInstance("noop_on_blue")
		ProcessInstanceEvent pinstanceGreen = createInstance("noop_on_green")

		then:
		spec.waitForProcessInstance(pinstanceBlue.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
		
		and:
		spec.waitForProcessInstance(pinstanceGreen.processInstanceKey) {
			state() == OperateClient.GetProcessInstanceResponse.State.ACTIVE
		}
	}

	void "Runs Zeebe jobs from multiple tags"() {
		given:
		deployScript('write_tagged_output_file', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Tasks
    Create File    output.txt    %{RPA_ZEEBE_JOB_TYPE}
    Upload Documents    output.txt    outputFile
''')
		and:
		deploySimpleRobotProcess(
				'write_tagged_output_file_on_blue',
				'write_tagged_output_file',
				'blue')

		deploySimpleRobotProcess(
				'write_tagged_output_file_on_red',
				'write_tagged_output_file',
				'red')

		when:
		ProcessInstanceEvent pinstanceBlue = createInstance("write_tagged_output_file_on_blue")
		ProcessInstanceEvent pinstanceRed = createInstance("write_tagged_output_file_on_red")

		then:
		spec.waitForProcessInstance(pinstanceBlue.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}

		and:
		spec.waitForProcessInstance(pinstanceRed.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}

		when:
		ZeebeDocumentDescriptor blueDocument = objectMapper.convertValue(
				spec.expectVariables(pinstanceBlue.processInstanceKey) {
					outputFile
				}.outputFile,
				ZeebeDocumentDescriptor)
		
		ZeebeDocumentDescriptor redDocument = objectMapper.convertValue(
				spec.expectVariables(pinstanceRed.processInstanceKey) {
					outputFile
				}.outputFile,
				ZeebeDocumentDescriptor)

		String blueContents = download(documentClient.getDocument(
				blueDocument.documentId(), blueDocument.storeId(), blueDocument.contentHash())).text
		
		String redContents = download(documentClient.getDocument(
				redDocument.documentId(), redDocument.storeId(), redDocument.contentHash())).text

		then:
		blueContents == "camunda::RPA-Task::blue"
		redContents == "camunda::RPA-Task::red"
	}
}
	
