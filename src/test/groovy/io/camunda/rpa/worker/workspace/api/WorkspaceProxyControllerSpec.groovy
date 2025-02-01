package io.camunda.rpa.worker.workspace.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.workspace.WorkspaceService
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.server.RequestPath
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import java.util.function.Supplier

class WorkspaceProxyControllerSpec extends Specification implements PublisherUtils {
	
	Function<Path, Flux<DataBuffer>> dataBuffersFactory = Mock()
	WorkspaceService workspaceService = Mock()
	IO io = Stub() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	
	@Subject
	WorkspaceProxyController controller = new WorkspaceProxyController(workspaceService, dataBuffersFactory, io)
	
	void "Proxies to contents of workspace file"() {
		given:
		ServerWebExchange exchange = Mock() {
			getRequest() >> Stub(ServerHttpRequest) {
				getPath() >> RequestPath.parse("/workspace/abc123/path/to/file.txt", null)
			}
		}
		List<DataBuffer> someDataBuffersList = [Stub(DataBuffer), Stub(DataBuffer)]
		Flux<DataBuffer> someDataBuffers = Flux.fromIterable(someDataBuffersList)
		Path workspaceFilePath = Stub()

		when:
		ResponseEntity<Flux<DataBuffer>> r = block controller.getWorkspaceFileContents("abc123", exchange)

		then:
		1 * workspaceService.getWorkspaceFile("abc123", "path/to/file.txt") >> Optional.of(
				new WorkspaceFile("text/plain", 123, workspaceFilePath))
		1 * dataBuffersFactory.apply(workspaceFilePath) >> someDataBuffers
		
		and:
		r.headers.getFirst(HttpHeaders.CONTENT_TYPE) == "text/plain"
		r.headers.getFirst(HttpHeaders.CONTENT_LENGTH) == "123"
		r.body == someDataBuffers
	}
	
	void "Proxies to contents of workspace file as attachment"() {
		given:
		ServerWebExchange exchange = Mock() {
			getRequest() >> Stub(ServerHttpRequest) {
				getPath() >> RequestPath.parse("/workspace/abc123/path/to/file.txt", null)
			}
		}
		List<DataBuffer> someDataBuffersList = [Stub(DataBuffer), Stub(DataBuffer)]
		Flux<DataBuffer> someDataBuffers = Flux.fromIterable(someDataBuffersList)
		Path workspaceFilePath = Paths.get("/path/to/workspaces/abc123/path/to/file.txt")

		when:
		ResponseEntity<Flux<DataBuffer>> r = block controller.getWorkspaceFileAttachment("abc123", exchange)

		then:
		1 * workspaceService.getWorkspaceFile("abc123", "path/to/file.txt") >> Optional.of(
				new WorkspaceFile("text/plain", 123, workspaceFilePath))
		1 * dataBuffersFactory.apply(workspaceFilePath) >> someDataBuffers

		and:
		r.headers.getFirst(HttpHeaders.CONTENT_TYPE) == "text/plain"
		r.headers.getFirst(HttpHeaders.CONTENT_LENGTH) == "123"
		r.body == someDataBuffers
		r.headers.getContentDisposition().type == "attachment"
		r.headers.getContentDisposition().filename == "file.txt"
	}
}
