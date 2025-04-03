package io.camunda.rpa.worker.files.api

import com.fasterxml.jackson.core.type.TypeReference
import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import io.camunda.rpa.worker.secrets.SecretsService
import io.camunda.rpa.worker.util.IterableMultiPart
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import okhttp3.MediaType
import okhttp3.MultipartReader
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.spockframework.spring.SpringBean
import org.spockframework.spring.SpringSpy
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.util.concurrent.PollingConditions

import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class FilesFunctionalSpec extends AbstractFunctionalSpec {
	
	private static final String WRITE_SOME_FILES_SCRIPT = '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Write Some Files
    Create File    outputs/one.yes    one
    Create File    outputs/two.yes    two
    Create File    outputs/three.no    three
    Create File    outputs/four.no    four
    
    Upload Documents    outputs/*.yes
'''
	
	private static final String DO_NOTHING_SCRIPT = '''\
*** Tasks ***
Do Nothing
	No Operation
'''
	
	@SpringSpy
	WorkspaceCleanupService workspaceCleanupService
	
	@SpringBean
	SecretsService secretsService = Stub() {
		getSecrets() >> Mono.just(Collections.emptyMap())
	}
	
	private List<ZeebeDocumentDescriptor.Metadata> uploadRequests = new CopyOnWriteArrayList<>()
	
	void "Request to store files triggers upload of workspace files to Zeebe"() {
		given:
		bypassZeebeAuth()
		Workspace theWorkspace
		workspaceCleanupService.preserveLast(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}
		
		and:
		zeebeApi.dispatcher = this.&copyInputToOutput

		when:
		EvaluateScriptResponse response = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(WRITE_SOME_FILES_SCRIPT, [:], null)))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
		.block()
		
		then:
		response.result() == ExecutionResults.Result.PASS
		
		and:
		2.times {
			with(zeebeApi.takeRequest(5, TimeUnit.SECONDS)) { req ->
				URI.create(req.path).path == "/v2/documents"
				req.method == HttpMethod.POST.toString()
			}
		}
		
		and:
		new PollingConditions().eventually {
			uploadRequests.size() == 2
			uploadRequests*.fileName().containsAll("one.yes", "two.yes")
		}	
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
		zeebeApi.setDispatcher { rr ->
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
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(DO_NOTHING_SCRIPT, [:], null)))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		then:
		response.result() == ExecutionResults.Result.PASS

		when:
		Map<String, FilesController.RetrieveFileResult> resp = block post()
				.uri("/file/retrieve/${theWorkspace.path().fileName.toString()}")
				.body(BodyInserters.fromValue([
						
						"input/file1.txt": new ZeebeDocumentDescriptor(
								"the-store", 
								"document-id-1", 
								null, 
								"file1-hash"),
						
						"input/file2.txt": new ZeebeDocumentDescriptor(
								"the-store", 
								"document-id-2", 
								null, 
								"file2-hash")]))
		
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, FilesController.RetrieveFileResult>>() {})

		then:
		resp.size() == 2
		resp['input/file1.txt'].result() == "OK"
		resp['input/file2.txt'].result() == "NOT_FOUND"
		
		and:
		Files.exists(theWorkspace.path().resolve("input/file1.txt"))
		theWorkspace.path().resolve("input/file1.txt").text == "File 1 contents"
		
		and:
		with([zeebeApi.takeRequest(1, TimeUnit.SECONDS), zeebeApi.takeRequest(1, TimeUnit.SECONDS)]) { reqs ->
			with(reqs.collect {  UriComponentsBuilder.fromUri(URI.create(it.path)).build().getQueryParams() }) { qs ->
				qs.collect { it.getFirst("storeId") }
						.every { it == "the-store" }

				qs.collect { it.getFirst("contentHash") }
						.containsAll(["file1-hash", "file2-hash"])
			}
		}
	}
	
	void "Request to store files looks up correct location in workspace"() {
		given:
		bypassZeebeAuth()
		Workspace theWorkspace
		workspaceCleanupService.preserveLast(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}

		and:
		zeebeApi.dispatcher = this.&copyInputToOutput

		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(WRITE_SOME_FILES_SCRIPT, [:], null)))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		then:
		response.result() == ExecutionResults.Result.PASS

		when:
		Map<String, ZeebeDocumentDescriptor> resp = block post()
				.uri("/file/store/${theWorkspace.path().fileName.toString()}")
				.body(BodyInserters.fromValue(new StoreFilesRequest("*.robot")))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, ZeebeDocumentDescriptor>>() {})

		then:
		resp.size() == 1
		resp['main.robot'].metadata().fileName() == "main.robot"
		resp.values()*.contentHash().every { it == "content-hash" }
	}

	private static final String UPLOAD_FILES_WITH_UNNORMALISED_PATHS_SCRIPT = '''\
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
'''

	@Issue("https://github.com/camunda/rpa-worker/issues/129")
	void "Correctly handles upload file requests with unnormalised paths"() {
		given:
		bypassZeebeAuth()
		Workspace theWorkspace
		workspaceCleanupService.preserveLast(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}

		and:
		zeebeApi.dispatcher = this.&copyInputToOutput

		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(UPLOAD_FILES_WITH_UNNORMALISED_PATHS_SCRIPT, [:], null)))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		then:
		response.result() == ExecutionResults.Result.PASS
		with(fileOrFiles(response.variables()['uploaded1'])) {
			size() == 1
			first().metadata().fileName() == "one.txt"
		}
		with(fileOrFiles(response.variables()['uploaded2'])) {
			size() == 1
			first().metadata().fileName() == "two.txt"
		}
		with(fileOrFiles(response.variables()['uploaded3'])) {
			size() == 2
			it*.metadata()*.fileName().containsAll(["two.txt", "three.txt"])
		}
	}

	private static final String UPLOAD_FILES_WITH_ABSOLUTE_PATHS_SCRIPT = '''\
*** Settings ***
Library    OperatingSystem
Library    Camunda

*** Tasks ***
Main
    Create File    %{RPA_WORKSPACE}/test.txt
    ${testDocument}=    Upload Documents    %{RPA_WORKSPACE}/test.txt
    Create Directory    downloaded
    Download Documents    ${testDocument}    %{RPA_WORKSPACE}/downloaded
    File Should Exist    downloaded/test.txt
'''

	@Issue("https://github.com/camunda/rpa-worker/issues/167")
	void "Correctly handles upload file requests with absolute paths"() {
		given:
		bypassZeebeAuth()
		Workspace theWorkspace
		workspaceCleanupService.preserveLast(_) >> { Workspace w ->
			theWorkspace = w
			return Mono.empty()
		}

		and:
		zeebeApi.setDispatcher { rr ->
			String path = URI.create(rr.path).path
			return path.endsWith("document-id")
					? new MockResponse().tap {
						setResponseCode(HttpStatus.OK.value())
						setBody("")
					}
					: copyInputToOutput(rr)
		}

		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(new EvaluateScriptRequest(UPLOAD_FILES_WITH_ABSOLUTE_PATHS_SCRIPT, [:], null)))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		then:
		response.result() == ExecutionResults.Result.PASS
	}


	private List<ZeebeDocumentDescriptor> fileOrFiles(def variable) {
		List<Map<String, Object>> filesRaw = variable instanceof List<Map<String, Object>> ? variable : [ variable ]
		return objectMapper.convertValue(filesRaw, new TypeReference<List<ZeebeDocumentDescriptor>>() {})
	}
	
	private MockResponse copyInputToOutput(RecordedRequest rr) {
		MultipartReader mpr = new MultipartReader(ResponseBody.create(
				rr.body.readUtf8(),
				MediaType.parse(rr.headers.get("Content-Type"))))

		Map<String, FormFieldPart> parts = new IterableMultiPart(mpr).collectEntries {
			[it.name(), it]
		}

		ZeebeDocumentDescriptor.Metadata metadata = objectMapper.readValue(parts.metadata.value(), ZeebeDocumentDescriptor.Metadata)
		uploadRequests << metadata

		new MockResponse().tap {
			setResponseCode(201)
			setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
			setBody(new JsonOutput().toJson([
					'camunda.document.type': 'camunda',
					storeId                : 'the-store',
					documentId             : 'document-id',
					contentHash            : 'content-hash',
					metadata               : [
							contentType: metadata.contentType(),
							fileName   : metadata.fileName(),
							size       : metadata.size()
					]
			]))
		}
	}
}
