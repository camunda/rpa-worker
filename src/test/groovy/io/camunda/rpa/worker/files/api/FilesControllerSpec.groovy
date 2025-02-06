package io.camunda.rpa.worker.files.api

import feign.FeignException
import feign.Request
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.files.DocumentClient
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.workspace.WorkspaceService
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties
import io.camunda.zeebe.spring.client.properties.common.AuthProperties
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpEntity
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
	
	Path workspace = Paths.get("/path/to/workspaces/workspace123456/")
	WorkspaceService workspaceService = Stub() {
		getById("workspace123456") >> Optional.of(workspace)
		getById(_) >> Optional.empty()
	}
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	
	ZeebeAuthenticationService zeebeAuthService = Stub() {
		getAuthToken("zeebe-token-audience") >> Mono.just(authToken)
	}
	DocumentClient documentClient = Stub()

	CamundaClientProperties camundaClientProperties = Stub(CamundaClientProperties) {
		getMode() >> CamundaClientProperties.ClientMode.saas
		getAuth() >> Stub(AuthProperties) {
			getClientId() >> "the-client-id"
		}
		getClusterId() >> "the-cluster-id"
		getRegion() >> "the-region"
		getZeebe() >> Stub(ZeebeClientProperties) {
			getGrpcAddress() >> "https://the-grpc-address".toURI()
			getRestAddress() >> "https://the-rest-address".toURI()
			isPreferRestOverGrpc() >> false
			getAudience() >> "zeebe-token-audience"
		}
	}
	
	@Subject
	FilesController controller = new FilesController(workspaceService, io, zeebeAuthService, documentClient, camundaClientProperties)
	
	void "Uploads files from workspace to Zeebe"() {
		given:
		Path matchingWorkspaceFile1 = workspace.resolve("outputs/one.pdf")
		Path matchingWorkspaceFile2 = workspace.resolve("outputs/two.pdf")
		Path nonMatchingWorkspaceFile = workspace.resolve("outputs/ignored.tmp")
		
		and:
		PathMatcher pathMatcher = Stub()
		io.globMatcher("**/*.pdf") >> pathMatcher
		
		and:
		io.walk(workspace) >> { 
			Stream.of(
					matchingWorkspaceFile1, 
					matchingWorkspaceFile2, 
					nonMatchingWorkspaceFile) 
		}
		
		and:
		documentClient.uploadDocument(authToken, _, _) >> { _, MultiValueMap<String, HttpEntity<?>> reqBody, Map params -> 
			Mono.just(new ZeebeDocumentDescriptor(
					"store-id", 
					"document-id", 
					new ZeebeDocumentDescriptor.Metadata(
							"content-type", 
							reqBody.getFirst("file").getHeaders().getContentDisposition().getFilename(), 
							null, 
							123)))
		}
		
		and:
		workspaceService.getWorkspaceFile("workspace123456", matchingWorkspaceFile1.toString()) >> Optional.of(
				new WorkspaceFile(workspace, "application/pdf", 123, matchingWorkspaceFile1))
		workspaceService.getWorkspaceFile("workspace123456", matchingWorkspaceFile2.toString()) >> Optional.of(
				new WorkspaceFile(workspace, "application/pdf", 123, matchingWorkspaceFile2))

		and:
		pathMatcher.matches(matchingWorkspaceFile1) >> true
		pathMatcher.matches(matchingWorkspaceFile2) >> true
		pathMatcher.matches(nonMatchingWorkspaceFile) >> false

		when:
		Map<String, ZeebeDocumentDescriptor> map = block controller.storeFiles(
				"workspace123456", new StoreFilesRequest("**/*.pdf"))
		
		then:
		map.size() == 2
		map['outputs/one.pdf'].metadata().fileName() == "outputs/one.pdf"
		map['outputs/two.pdf'].metadata().fileName() == "outputs/two.pdf"
	}
	
	void "Downloads files from Zeebe into workspace"() {
		given:
		Path file1Destination = workspace.resolve("input/file1.txt")
		documentClient.getDocument(authToken, "document-id-1", _) >> Flux.error(
				new FeignException.NotFound("", new Request(Request.HttpMethod.GET, "", [:], null, null), [] as byte[], [:]))
		
		Path file2Destination = workspace.resolve("input/file2.txt")
		documentClient.getDocument(authToken, "document-id-2", _) >> Flux.empty()
		
		Path file3Destination = workspace.resolve("input/file3.txt")
		documentClient.getDocument(authToken, "document-id-3", _) >> Flux.empty()


		when:
		Map<String, FilesController.RetrieveFileResult> r = block controller.retrieveFiles("workspace123456", [
				"input/file1.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-1",
						new ZeebeDocumentDescriptor.Metadata("text/plain", "file1.txt", null, 123)),

				"input/file2.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-2",
						new ZeebeDocumentDescriptor.Metadata("text/plain", "file2.txt", null, 123)),

				"input/file3.txt": new ZeebeDocumentDescriptor(
						"store-id",
						"document-id-3",
						new ZeebeDocumentDescriptor.Metadata("text/plain", "file3.txt", null, 123))
		])
		
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
}
