package io.camunda.rpa.worker.files

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.zeebe.ZeebeJobInfo
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpEntity
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths

class FilesServiceSpec extends Specification implements PublisherUtils {
	
	DocumentClient documentClient = Mock()
	ObjectProvider<DocumentClient> documentClientProvider = Stub() {
		getObject() >> documentClient
	}
	
	@Subject
	FilesService service = new FilesService(documentClientProvider)

	Path workspacePath = Paths.get("/path/to/workspace/")
	Workspace workspace = new Workspace("workspace-id", workspacePath)
	Path filePath = workspacePath.resolve("file-name")
	WorkspaceFile workspaceFile = new WorkspaceFile(workspace, "text/plain", 3, filePath)
	ZeebeJobInfo jobInfo = new ZeebeJobInfo("process-def-id", 123L)

	void "Uploads document and returns correct document descriptor"() {
		when:
		ZeebeDocumentDescriptor r = block service.uploadDocument(workspaceFile, FilesService.toMetadata(workspaceFile, jobInfo))

		then:
		1 * documentClient.uploadDocument(_, _) >> { MultiValueMap<String, HttpEntity<?>> reqBody, Map params ->
			return Mono.just(new ZeebeDocumentDescriptor(
					"store-id",
					"document-id",
					new ZeebeDocumentDescriptor.Metadata(
							"content-type",
							reqBody.getFirst("file").getHeaders().getContentDisposition().getFilename(),
							null,
							123,
							"proc-def-id",
							234,
							[:]),
					"filehashfromserver"))
		}

		and:
		r.documentId() == "document-id"
		r.storeId() == "store-id"
		r.metadata() == new ZeebeDocumentDescriptor.Metadata("content-type", "file-name", null, 123, "proc-def-id", 234, [:])
		r.contentHash() == "filehashfromserver"
	}

	void "Fetches specified document"() {
		given:
		Flux<DataBuffer> expected = Stub()
		
		when:
		Flux<DataBuffer> r = service.getDocument("document-id", "store-id", "content-hash")
		
		then:
		1 * documentClient.getDocument("document-id", "store-id", "content-hash") >> expected
		
		and:
		r == expected
	}
}
