package io.camunda.rpa.worker.files

import com.fasterxml.jackson.core.type.TypeReference
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import spock.lang.PendingFeature

import java.time.Duration

// TODO: Using the Sandbox for now, but should use Zeebe job when available
class FileHandlingE2ESpec extends AbstractE2ESpec {

	@Autowired
	DocumentClient documentClient
	
	void "Single file is provided to workspace"() {
		given:
		ZeebeDocumentDescriptor uploaded = zeebeToken.flatMap { token ->
			documentClient.uploadDocument(token, uploadDocumentRequest("one.txt", "one"), null)
		}.block()
		
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Download the file
    Download Documents    ${theFile}
    ${fileContents}=    Get File    one.txt
    Should Be Equal    ${fileContents}    one
''')
						.variables([theFile: uploaded])
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
	}

	void "Multiple files are provided to workspace"() {
		given:
		ZeebeDocumentDescriptor uploaded1 = zeebeToken.flatMap { token ->
			documentClient.uploadDocument(token, uploadDocumentRequest("one.txt", "one"), null)
		}.block()
		ZeebeDocumentDescriptor uploaded2 = zeebeToken.flatMap { token ->
			documentClient.uploadDocument(token, uploadDocumentRequest("two.txt", "two"), null)
		}.block()

		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
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
						.variables([theFiles: [uploaded1, uploaded2]])
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
	}
	
	@PendingFeature(reason = "Files are downloaded into a directory with the custom name with the original name?")
	void "Single file is provided to workspace - custom filename"() {
		given:
		ZeebeDocumentDescriptor uploaded = zeebeToken.flatMap { token ->
			documentClient.uploadDocument(token, uploadDocumentRequest("one.txt", "one"), null)
		}.block()

		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Download the file
    Download Documents    ${theFile}    downloaded.txt
    ${fileContents}=    Get File    downloaded.txt
    Should Be Equal    ${fileContents}    one
''')
						.variables([theFile: uploaded])
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
	}
	
	void "Single file is uploaded from workspace"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Upload the file
    Create File    one.txt    one
    ${uploadedFile}=    Upload Documents    one.txt
    Set Output Variable    uploadedFile    ${uploadedFile}
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
		
		when:
		ZeebeDocumentDescriptor uploaded = objectMapper.convertValue(r.variables()['uploadedFile'], ZeebeDocumentDescriptor)
		String contents = download(zeebeToken.flatMapMany { token ->
			documentClient.getDocument(token, uploaded.documentId(), uploaded.storeId(), uploaded.contentHash())
		}).text
		
		then:
		contents == "one"
	}

	void "Single file is uploaded from workspace - alt out variable"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Upload the file
    Create File    one.txt    one
    Upload Documents    one.txt    uploadedFile
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS

		when:
		ZeebeDocumentDescriptor uploaded = objectMapper.convertValue(r.variables()['uploadedFile'], ZeebeDocumentDescriptor)
		String contents = download(zeebeToken.flatMapMany { token ->
			documentClient.getDocument(token, uploaded.documentId(), uploaded.storeId(), uploaded.contentHash())
		}).text

		then:
		contents == "one"
	}

	void "Multiple files are uploaded from workspace"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Upload the file
    Create File    one.txt    one
    Create File    two.txt    two
    Upload Documents    *.txt    uploadedFiles
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS

		when:
		List<ZeebeDocumentDescriptor> uploaded = objectMapper.convertValue(r.variables()['uploadedFiles'],
				new TypeReference<List<ZeebeDocumentDescriptor>>() {})
		String contents1 = download(zeebeToken.flatMapMany { token ->
			uploaded.find { it.metadata().fileName() == "one.txt" }.with { zdd ->
				documentClient.getDocument(token, zdd.documentId(), zdd.storeId(), zdd.contentHash())
			}
		}).text
		String contents2 = download(zeebeToken.flatMapMany { token ->
			uploaded.find { it.metadata().fileName() == "two.txt" }.with { zdd ->
				documentClient.getDocument(token, zdd.documentId(), zdd.storeId(), zdd.contentHash())
			}
		}).text

		then:
		contents1 == "one"
		contents2 == "two"
	}

	private static MultiValueMap<String, HttpEntity<?>> uploadDocumentRequest(String filename, String content) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();

		builder.part("metadata", new ZeebeDocumentDescriptor.Metadata(
				"text/plain",
				filename,
				null,
				content.length(),
				null,
				null,
				Collections.emptyMap()))
				.contentType(MediaType.APPLICATION_JSON);

		builder.part("file", content, MediaType.TEXT_PLAIN)

		return builder.build();
	}
	
	private static InputStream download(Flux<DataBuffer> source) {
		return DataBufferUtils.subscriberInputStream(source, 1)
	}
}
