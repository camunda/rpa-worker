package io.camunda.rpa.worker.files.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.files.DocumentClient
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.workspace.WorkspaceService
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService
import org.springframework.http.HttpEntity
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.function.Supplier
import java.util.stream.Stream

class FilesControllerSpec extends Specification implements PublisherUtils {

	WorkspaceService workspaceService = Stub()
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	
	ZeebeAuthenticationService zeebeAuthService = Stub()
	DocumentClient documentClient = Stub()
	
	@Subject
	FilesController controller = new FilesController(workspaceService, io, zeebeAuthService, documentClient)
	
	void "Uploads files from workspace to Zeebe"() {
		given:
		Path workspace = Stub(Path)
		Path matchingWorkspaceFile1 = Stub(Path) {
			toString() >> "outputs/one.pdf"
		}
		Path matchingWorkspaceFile2 = Stub(Path) {
			toString() >> "outputs/two.pdf"
		}
		Path nonMatchingWorkspaceFile = Stub(Path)
		workspaceService.getById("workspace123456") >> Optional.of(workspace)
		
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
		zeebeAuthService.getAuthToken(FilesController.ZEEBE_TOKEN_AUDIENCE) >> Mono.just("the-auth-token")
		documentClient.uploadDocument("the-auth-token", _, _) >> { _, MultiValueMap<String, HttpEntity<?>> reqBody, Map params -> 
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
		workspaceService.getWorkspaceFile("workspace123456", "outputs/one.pdf") >> Optional.of(
				new WorkspaceFile("application/pdf", 123, matchingWorkspaceFile1))
		workspaceService.getWorkspaceFile("workspace123456", "outputs/two.pdf") >> Optional.of(
				new WorkspaceFile("application/pdf", 123, matchingWorkspaceFile2))

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
}
