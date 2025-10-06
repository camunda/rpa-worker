package io.camunda.rpa.worker.files

import com.fasterxml.jackson.core.type.TypeReference
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.util.MultiValueMap
import spock.lang.Issue

class FileHandlingE2ESpec extends AbstractE2ESpec {

	void "Single file is provided to workspace"() {
		given:
		deployScript("download_from_zeebe", '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Download the file
    Download Documents    ${theFile}
    ${fileContents}=    Get File    one.txt
    Should Be Equal    ${fileContents}    one
''')
		
		and:
		deploySimpleRobotProcess("download_from_zeebe_on_default", "download_from_zeebe")
		
		and:
		ZeebeDocumentDescriptor uploaded = documentClient.uploadDocument(
				uploadDocumentRequest("one.txt", "one"), null).block()

		when:
		ProcessInstanceEvent pinstance = createInstance("download_from_zeebe_on_default", 
				theFile: uploaded)

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}

	void "Multiple files are provided to workspace"() {
		given:
		deployScript('download_multiple_from_zeebe', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Download the file
    Download Documents    ${theFiles}
    ${fileContents1}=    Get File    one.txt
    ${fileContents2}=    Get File    two.txt
    Should Be Equal    ${fileContents1}    one
    Should Be Equal    ${fileContents2}    two
''')
		
		and:
		deploySimpleRobotProcess('download_multiple_from_zeebe_on_default', 'download_multiple_from_zeebe')
		
		and:
		ZeebeDocumentDescriptor uploaded1 = documentClient.uploadDocument(
				uploadDocumentRequest("one.txt", "one"), null).block()
		ZeebeDocumentDescriptor uploaded2 = documentClient.uploadDocument(
				uploadDocumentRequest("two.txt", "two"), null).block()

		when:
		ProcessInstanceEvent pinstance = createInstance('download_multiple_from_zeebe_on_default',
				theFiles: [uploaded1, uploaded2])

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}
	
	void "Single file is provided to workspace - custom output directory"() {
		given:
		deployScript('download_from_zeebe_custom_dir', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Download the file
    Download Documents    ${theFile}    downloaded
    ${fileContents}=    Get File    downloaded/one.txt
    Should Be Equal    ${fileContents}    one
''')

		and:
		deploySimpleRobotProcess(
				'download_from_zeebe_custom_dir_on_default',
				'download_from_zeebe_custom_dir')

		and:
		ZeebeDocumentDescriptor uploaded = documentClient.uploadDocument(
				uploadDocumentRequest("one.txt", "one"), null).block()

		when:
		ProcessInstanceEvent pinstance = createInstance('download_from_zeebe_custom_dir_on_default',
				theFile: uploaded)

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
	}
	
	void "Single file is uploaded from workspace"() {
		given:
		deployScript('upload_to_zeebe', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Upload the file
    Create File    one.txt    one
    ${uploadedFile}=    Upload Documents    one.txt
    Set Output Variable    uploadedFile    ${uploadedFile}
''')
		
		and:
		deploySimpleRobotProcess('upload_to_zeebe_on_default', 'upload_to_zeebe')
		
		when:
		ProcessInstanceEvent pinstance = createInstance('upload_to_zeebe_on_default')

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}
		
		when:
		ZeebeDocumentDescriptor uploaded = objectMapper.convertValue(
				spec.expectVariables(pinstance.processInstanceKey) {
					uploadedFile
				}.uploadedFile,
				new TypeReference<List<ZeebeDocumentDescriptor>>() {})
				.first()

		String contents = download(documentClient.getDocument(
				uploaded.documentId(), uploaded.storeId(), uploaded.contentHash())).text
		
		then:
		contents == "one"
	}

	void "Single file is uploaded from workspace - alt out variable"() {
		given:
		deployScript('upload_to_zeebe_alt_out', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Upload the file
    Create File    one.txt    one
    Upload Documents    one.txt    uploadedFile
''')
		
		and:
		deploySimpleRobotProcess('upload_to_zeebe_alt_out_on_default', 'upload_to_zeebe_alt_out')

		when:
		ProcessInstanceEvent pinstance = createInstance('upload_to_zeebe_alt_out_on_default')

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}

		when:
		ZeebeDocumentDescriptor uploaded = objectMapper.convertValue(
				spec.expectVariables(pinstance.processInstanceKey) {
					uploadedFile
				}.uploadedFile,
				new TypeReference<List<ZeebeDocumentDescriptor>>() {})
				.first()

		String contents = download(documentClient.getDocument(
				uploaded.documentId(), uploaded.storeId(), uploaded.contentHash())).text

		then:
		contents == "one"
	}

	void "Multiple files are uploaded from workspace"() {
		given:
		deployScript('upload_multiple_to_zeebe', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Upload the file
    Create File    one.txt    one
    Create File    two.txt    two
    Upload Documents    *.txt    uploadedFiles
''')
		
		and:
		deploySimpleRobotProcess('upload_multiple_to_zeebe_on_default', 'upload_multiple_to_zeebe')

		when:
		ProcessInstanceEvent pinstance = createInstance('upload_multiple_to_zeebe_on_default')

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}

		when:
		List<ZeebeDocumentDescriptor> uploaded = objectMapper.convertValue(
				spec.expectVariables(pinstance.processInstanceKey) {
					uploadedFiles
				}.uploadedFiles,
				new TypeReference<List<ZeebeDocumentDescriptor>>() {})

		String contents1 = download(uploaded.find {
			it.metadata().fileName() == "one.txt" }.with { zdd ->
				documentClient.getDocument(zdd.documentId(), zdd.storeId(), zdd.contentHash())
		}).text

		String contents2 = download(uploaded.find {
			it.metadata().fileName() == "two.txt" }.with { zdd ->
				documentClient.getDocument(zdd.documentId(), zdd.storeId(), zdd.contentHash())
		}).text

		then:
		contents1 == "one"
		contents2 == "two"
	}

	@Issue("https://github.com/camunda/rpa-worker/issues/129")
	void "Multiple files are uploaded from workspace - unnormalised paths"() {
		given:
		deployScript('upload_multiple_to_zeebe_unnormalised', '''\
*** Settings ***
Library    Camunda
Library    OperatingSystem

*** Tasks ***
Test
    Create File    one.txt
    Create File    two/two.txt
    Create File    two/three.txt
    Create directory    two/four
    Upload Documents    ./one.txt    uploaded1
    Upload Documents    two/four/../two.txt    uploaded2
    Upload Documents    ./tw*/four/../*.txt    uploaded3
''')

		and:
		deploySimpleRobotProcess('upload_multiple_to_zeebe_unnormalised_on_default', 'upload_multiple_to_zeebe_unnormalised')

		when:
		ProcessInstanceEvent pinstance = createInstance('upload_multiple_to_zeebe_unnormalised_on_default')

		then:
		spec.waitForProcessInstance(pinstance.processInstanceKey) {
			expectNoIncident(it.key())
			state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
		}

		when:
		Map<String, List<ZeebeDocumentDescriptor>> uploaded = objectMapper.convertValue(
				spec.expectVariables(pinstance.processInstanceKey) {
					uploaded1
					uploaded2
					uploaded3
				}.with {
					[uploaded1: uploaded1, uploaded2: uploaded2, uploaded3: uploaded3]
				}.collectEntries { k, v -> [k, v instanceof List ? v : [v] ] },
				new TypeReference<Map<String, List<ZeebeDocumentDescriptor>>>() {})

		then:
		uploaded['uploaded1'].size() == 1
		uploaded['uploaded2'].size() == 1
		uploaded['uploaded3'].size() == 2
	}

	private static MultiValueMap<String, HttpEntity<?>> uploadDocumentRequest(String filename, String content) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder()

		builder.part("metadata", new ZeebeDocumentDescriptor.Metadata(
				"text/plain",
				filename,
				null,
				content.length(),
				null,
				null,
				Collections.emptyMap()))
				.contentType(MediaType.APPLICATION_JSON)

		builder.part("metadata88", new ZeebeDocumentDescriptor.Metadata88(
				"text/plain",
				filename,
				null,
				content.length(),
				null,
				null,
				Collections.emptyMap()))
				.contentType(MediaType.APPLICATION_JSON)

		builder.part("file", content, MediaType.TEXT_PLAIN)

		return builder.build()
	}
}
