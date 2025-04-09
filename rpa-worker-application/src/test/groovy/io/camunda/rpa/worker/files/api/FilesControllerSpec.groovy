package io.camunda.rpa.worker.files.api

import feign.FeignException
import feign.Request
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.StubbedResponseGenerator
import io.camunda.rpa.worker.files.DocumentClient
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.workspace.WorkspaceService
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.function.Supplier
import java.util.stream.Stream

class FilesControllerSpec extends Specification implements PublisherUtils {

	static final String authToken = "the-auth-token"
	
	Workspace workspace = new Workspace("workspace123456", Paths.get("/path/to/workspaces/workspace123456/"), [:], null)
	WorkspaceService workspaceService = Stub() {
		getById("workspace123456") >> Optional.of(workspace)
		getById(_) >> Optional.empty()
	}
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
		wrap(_) >> { Mono<?> m -> m }
	}

	DocumentClient documentClient = Stub()
	StubbedResponseGenerator stubbedResponseGenerator = Stub()

	@Subject
	FilesController controller = new FilesController(workspaceService, io, documentClient, stubbedResponseGenerator)
	
	void "Uploads files from workspace to Zeebe"() {
		given:
		stubbedResponseGenerator.stubbedResponse("DocumentClient", "uploadDocument", _) >> Mono.empty()
		
		and:
		Path matchingWorkspaceFile1 = workspace.path().resolve("outputs/one.pdf")
		Path matchingWorkspaceFile2 = workspace.path().resolve("outputs/two.pdf")
		Path nonMatchingWorkspaceFile = workspace.path().resolve("outputs/ignored.tmp")
		
		and:
		PathMatcher pathMatcher = Stub()
		io.globMatcher("**/*.pdf") >> pathMatcher
		
		and:
		io.walk(workspace.path()) >> { 
			Stream.of(
					matchingWorkspaceFile1, 
					matchingWorkspaceFile2, 
					nonMatchingWorkspaceFile) 
		}
		
		and:
		documentClient.uploadDocument(_, _) >> { MultiValueMap<String, HttpEntity<?>> reqBody, Map params -> 
			Mono.just(new ZeebeDocumentDescriptor(
					"store-id",
					"document-id",
					new ZeebeDocumentDescriptor.Metadata(
							"content-type", 
							reqBody.getFirst("file").getHeaders().getContentDisposition().getFilename(), 
							null, 
							123, 
							null, 
							null, 
							[:]), 
					"filehashfromserver"))
			
		}
		
		and:
		workspaceService.getWorkspaceFile(workspace, workspace.path().relativize(matchingWorkspaceFile1).toString()) >> Optional.of(
				new WorkspaceFile(workspace, "application/pdf", 123, matchingWorkspaceFile1))
		workspaceService.getWorkspaceFile(workspace, workspace.path().relativize(matchingWorkspaceFile2).toString()) >> Optional.of(
				new WorkspaceFile(workspace, "application/pdf", 123, matchingWorkspaceFile2))

		and:
		pathMatcher.matches(workspace.path().relativize(matchingWorkspaceFile1)) >> true
		pathMatcher.matches(workspace.path().relativize(matchingWorkspaceFile2)) >> true
		pathMatcher.matches(workspace.path().relativize(nonMatchingWorkspaceFile)) >> false

		when:
		Map<String, ZeebeDocumentDescriptor> map = block(controller.storeFiles(
				"workspace123456", new StoreFilesRequest("**/*.pdf")))
				.getBody()
		
		then:
		map.size() == 2
		map['outputs/one.pdf'].metadata().fileName() == "outputs/one.pdf"
		map['outputs/two.pdf'].metadata().fileName() == "outputs/two.pdf"
		map.values()*.contentHash().every { it == "filehashfromserver" } 
	}
	
	void "Downloads files from Zeebe into workspace"() {
		given:
		stubbedResponseGenerator.stubbedResponse("DocumentClient", "getDocument", _) >> Mono.empty()

		and:
		Path file1Destination = workspace.path().resolve("input/file1.txt")
		documentClient.getDocument("document-id-1", "store-id", _) >> Flux.error(
				new FeignException.NotFound("", new Request(Request.HttpMethod.GET, "", [:], null, null), [] as byte[], [:]))
		
		Path file2Destination = workspace.path().resolve("input/file2.txt")
		documentClient.getDocument("document-id-2", "store-id", _) >> Flux.empty()
		
		Path file3Destination = workspace.path().resolve("input/file3.txt")
		documentClient.getDocument("document-id-3", "store-id", _) >> Flux.empty()


		when:
		Map<String, FilesController.RetrieveFileResult> r = block(controller.retrieveFiles("workspace123456", [
				"input/file1.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-1",
						new ZeebeDocumentDescriptor.Metadata(
								"text/plain", 
								"file1.txt", 
								null, 
								123, 
								null, 
								null, 
								[:]),
						"f1hash"),

				"input/file2.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-2",
						new ZeebeDocumentDescriptor.Metadata(
								"text/plain", 
								"file2.txt", 
								null, 
								123, 
								null, 
								null, 
								[:]),
						"f2hash"),

				"input/file3.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-3",
						new ZeebeDocumentDescriptor.Metadata(
								"text/plain", 
								"file3.txt", 
								null, 
								123, 
								null, 
								null, 
								[:]), 
						"f3hash")
		])).getBody()
		
		then:
		1 * io.write(_ as Flux<DataBuffer>, file1Destination) >> { Flux<DataBuffer> data, Path dest, _ -> 
			return data.then()
		}
		1 * io.write(_ as Flux<DataBuffer>, file2Destination) >> { Flux<DataBuffer> data, Path dest, _ ->
			return Mono.error(new IOException())
		}
		1 * io.write(_ as Flux<DataBuffer>, file3Destination) >> { Flux<DataBuffer> data, Path dest, _ ->
			return data.then()
		}

		and:
		r.size() == 3
		r['input/file1.txt'].result() == "NOT_FOUND"
		r['input/file2.txt'].result() == "ERROR"
		r['input/file3.txt'].result() == "OK"
	}
	
	void "Returns stubbed response for upload"() {
		given:
		ResponseEntity<?> stubbedResponse = Stub()
		stubbedResponseGenerator.stubbedResponse(
				"DocumentClient", 
				"uploadDocument",
				{ Map m -> m.size() == 2 }) >> Mono.just(stubbedResponse)
		
		and:
		Path matchingWorkspaceFile1 = workspace.path().resolve("outputs/one.pdf")
		Path matchingWorkspaceFile2 = workspace.path().resolve("outputs/two.pdf")
		Path nonMatchingWorkspaceFile = workspace.path().resolve("outputs/ignored.tmp")

		and:
		PathMatcher pathMatcher = Stub()
		io.globMatcher("**/*.pdf") >> pathMatcher

		and:
		io.walk(workspace.path()) >> {
			Stream.of(
					matchingWorkspaceFile1,
					matchingWorkspaceFile2,
					nonMatchingWorkspaceFile)
		}

		and:
		workspaceService.getWorkspaceFile(workspace, workspace.path().relativize(matchingWorkspaceFile1).toString()) >> Optional.of(
				new WorkspaceFile(workspace, "application/pdf", 123, matchingWorkspaceFile1))
		workspaceService.getWorkspaceFile(workspace, workspace.path().relativize(matchingWorkspaceFile2).toString()) >> Optional.of(
				new WorkspaceFile(workspace, "application/pdf", 123, matchingWorkspaceFile2))

		and:
		pathMatcher.matches(workspace.path().relativize(matchingWorkspaceFile1)) >> true
		pathMatcher.matches(workspace.path().relativize(matchingWorkspaceFile2)) >> true
		pathMatcher.matches(workspace.path().relativize(nonMatchingWorkspaceFile)) >> false

		when:
		ResponseEntity<?> r = block controller.storeFiles(
				"workspace123456", new StoreFilesRequest("**/*.pdf"))

		then:
		r == stubbedResponse
	}

	void "Returns stubbed response for download"() {
		given:
		ResponseEntity<?> stubbedResponse = Stub()
		stubbedResponseGenerator.stubbedResponse("DocumentClient", "getDocument", _) >> Mono.just(stubbedResponse)
		
		when:
		ResponseEntity<?> r = block(controller.retrieveFiles("workspace123456", [
				"input/file1.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-1",
						new ZeebeDocumentDescriptor.Metadata(
								"text/plain",
								"file1.txt",
								null,
								123,
								null,
								null,
								[:]),
						"f1hash"),
		]))

		then:
		r == stubbedResponse
	}

}
