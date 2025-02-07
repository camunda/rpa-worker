package io.camunda.rpa.worker.files.api

import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import io.camunda.rpa.worker.util.IterableMultiPart
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import okhttp3.MediaType
import okhttp3.MultipartReader
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import org.spockframework.spring.SpringSpy
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono

import java.nio.file.Files

class FilesFunctionalSpec extends AbstractFunctionalSpec {
	
	// TODO: Use Camunda Robot library when available
	private static final String WRITE_SOME_FILES_SCRIPT = '''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Write Some Files
    Create File    outputs/one.yes    one
    Create File    outputs/two.yes    two
    Create File    outputs/three.no    three
    Create File    outputs/four.no    four
'''
	
	private static final String DO_NOTHING_SCRIPT = '''\
*** Tasks ***
Do Nothing
	No Operation
'''
	
	@SpringSpy
	WorkspaceCleanupService workspaceCleanupService
	
	void "Request to store files triggers upload of workspace files to Zeebe"() {
		given:
		bypassZeebeAuth()
		Workspace theWorkspace
		workspaceCleanupService.preserveLast(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}
		
		and:
		zeebeDocuments.setDispatcher { rr ->

			MultipartReader mpr = new MultipartReader(ResponseBody.create(
					rr.body.readUtf8(),
					MediaType.parse(rr.headers.get("Content-Type"))))

			Map<String, FormFieldPart> parts = new IterableMultiPart(mpr).collectEntries {
				[it.name(), it]
			}

			ZeebeDocumentDescriptor.Metadata metadata = objectMapper.readValue(parts.metadata.value(), ZeebeDocumentDescriptor.Metadata)
			
			new MockResponse().tap {
				setResponseCode(201)
				setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				setBody(new JsonOutput().toJson([
						'camunda.document.type': 'camunda',
						storeId: 'the-store',
						documentId: 'document-id',
						metadata: [
								contentType: metadata.contentType(),
								fileName: metadata.fileName(),
								size: metadata.size()
						]
				]))
			} 
		}

		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(WRITE_SOME_FILES_SCRIPT, [:])))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
		
		then:
		response.result() == ExecutionResults.Result.PASS

		when:
		Map<String, ZeebeDocumentDescriptor> resp = block post()
				.uri("/file/store/${theWorkspace.path().fileName.toString()}")
				.body(BodyInserters.fromValue(new StoreFilesRequest("**/*.yes")))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, ZeebeDocumentDescriptor>>() {})
		
		then:
		resp.size() == 2
		resp['one.yes'].metadata().fileName() == "one.yes"
		resp['two.yes'].metadata().fileName() == "two.yes"
	}
	
	void "Request to retrieve files downloads from Zeebe into workspace"() {
		given:
		bypassZeebeAuth()
		Workspace theWorkspace
		workspaceCleanupService.preserveLast(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}
		
		and:
		zeebeDocuments.setDispatcher { rr ->
			String path = URI.create(rr.path).path
			return path.endsWith("document-id-1")
					? new MockResponse().tap {
						setResponseCode(HttpStatus.OK.value())
						setBody("File 1 contents")
					}
					: new MockResponse().tap {
						setResponseCode(HttpStatus.NOT_FOUND.value())
					}
		}

		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(DO_NOTHING_SCRIPT, [:])))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		then:
		response.result() == ExecutionResults.Result.PASS

		when:
		Map<String, FilesController.RetrieveFileResult> resp = block post()
				.uri("/file/retrieve/${theWorkspace.path().fileName.toString()}")
				.body(BodyInserters.fromValue([
						"input/file1.txt": new ZeebeDocumentDescriptor("the-store", "document-id-1", null, null),
						"input/file2.txt": new ZeebeDocumentDescriptor("the-store", "document-id-2", null, null)]))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, FilesController.RetrieveFileResult>>() {})

		then:
		resp.size() == 2
		resp['input/file1.txt'].result() == "OK"
		resp['input/file2.txt'].result() == "NOT_FOUND"
		
		and:
		Files.exists(theWorkspace.path().resolve("input/file1.txt"))
		theWorkspace.path().resolve("input/file1.txt").text == "File 1 contents"
	}
}
