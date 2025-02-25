package io.camunda.rpa.worker.files

import com.fasterxml.jackson.core.type.TypeReference
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Flux
import spock.lang.PendingFeature

class FileHandlingE2ESpec extends AbstractE2ESpec {

	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [CAMUNDA_RPA_SCRIPTS_SOURCE: 'zeebe']
	}

	@Autowired
	DocumentClient documentClient
	
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
	
	@PendingFeature(reason = "Files are downloaded into a directory with the custom name with the original name?")
	void "Single file is provided to workspace - custom filename"() {
		given:
		deployScript('download_from_zeebe_custom_filename', '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Download the file
    Download Documents    ${theFile}    downloaded.txt
    ${fileContents}=    Get File    downloaded.txt
    Should Be Equal    ${fileContents}    one
''')

		and:
		deploySimpleRobotProcess(
				'download_from_zeebe_custom_filename_on_default',
				'download_from_zeebe_custom_filename')

		and:
		ZeebeDocumentDescriptor uploaded = documentClient.uploadDocument(
				uploadDocumentRequest("one.txt", "one"), null).block()

		when:
		ProcessInstanceEvent pinstance = createInstance('download_from_zeebe_custom_filename_on_default',
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
		ZeebeDocumentDescriptor uploaded = objectMapper.readValue(
				getInstanceVariables(pinstance.processInstanceKey)['uploadedFile'], 
				ZeebeDocumentDescriptor)

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
		ZeebeDocumentDescriptor uploaded = objectMapper.readValue(
				getInstanceVariables(pinstance.processInstanceKey)['uploadedFile'],
				ZeebeDocumentDescriptor)

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
		List<ZeebeDocumentDescriptor> uploaded = objectMapper.readValue(
				getInstanceVariables(pinstance.processInstanceKey)['uploadedFiles'],
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

		builder.part("file", content, MediaType.TEXT_PLAIN)

		return builder.build()
	}
	
	private static InputStream download(Flux<DataBuffer> source) {
		return DataBufferUtils.subscriberInputStream(source, 1)
	}
}
