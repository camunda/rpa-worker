package io.camunda.rpa.worker.workspace

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.function.Supplier
import java.util.stream.Stream

class WorkspaceServiceSpec extends Specification implements PublisherUtils {

	IO io = Mock() {
		supply(_) >> { Supplier fn -> return Mono.fromSupplier(fn) }
		createTempDirectory(_) >> Paths.get("/path/to/workspaces/")
	}
	
	@Subject
	WorkspaceService service = new WorkspaceService(io)
	
	void "Creates workspaces root dir on init"() {
		when:
		service.doInit()
		
		then:
		1 * io.createTempDirectory("rpa-workspaces") >> Stub(Path)
	}
	
	void "Creates new workspaces"() {
		given:
		service.doInit()
		Path rootDir = service.workspacesDir
		Path theWorkspace = Paths.get("/path/to/workspace123456/")

		when:
		Path workspace = service.createWorkspace()
		
		then:
		1 * io.createTempDirectory(rootDir, "workspace") >> theWorkspace
		1 * io.writeString(theWorkspace.resolve(".workspace"), "workspace123456", StandardOpenOption.CREATE_NEW)
		
		and:
		workspace == theWorkspace
	}
	
	void "Returns workspace by ID"() {
		given:
		service.doInit()
		
		and:
		Path theWorkspace = Paths.get("/path/to/workspaces/workspace123456/")
		
		when:
		Optional<Path> workspace = service.getById("workspace123456")
		
		then:
		1 * io.notExists(theWorkspace) >> false
		1 * io.isDirectory(theWorkspace) >> true
		1 * io.notExists(theWorkspace.resolve(".workspace")) >> false
		1 * io.readString(theWorkspace.resolve(".workspace")) >> "workspace123456"
		
		and:
		workspace.get() == theWorkspace
	}
	
	void "Returns empty when workspace is in incorrect location"() {
		given:
		service.doInit()

		when:
		Optional<Path> workspace = service.getById("../../workspace123456")

		then:
		! workspace.isPresent()
	}

	void "Returns empty when workspace does not exist"() {
		given:
		service.doInit()

		and:
		Path theWorkspace = Paths.get("/path/to/workspaces/workspace123456/")

		when:
		Optional<Path> workspace = service.getById("workspace123456")

		then:
		1 * io.notExists(theWorkspace) >> true

		and:
		! workspace.isPresent()

		when:
		Optional<Path> workspace2 = service.getById("workspace123456")

		then:
		1 * io.notExists(theWorkspace) >> false
		1 * io.isDirectory(theWorkspace) >> false

		and:
		! workspace2.isPresent()
	}
	
	void "Returns empty when no correct workspace ID file"() {
		given:
		service.doInit()
		Path theWorkspace = Paths.get("/path/to/workspaces/workspace123456/")
		io.notExists(theWorkspace) >> false
		io.isDirectory(theWorkspace) >> true
		Path workspaceIdFile = theWorkspace.resolve(".workspace")

		when:
		Optional<Path> workspace = service.getById("workspace123456")

		then:
		1 * io.notExists(workspaceIdFile) >> true

		and:
		! workspace.isPresent()

		when:
		Optional<Path> workspace2 = service.getById("workspace123456")

		then:
		1 * io.notExists(workspaceIdFile) >> false
		1 * io.readString(workspaceIdFile) >> "workspace789"

		and:
		! workspace2.isPresent()
	}

	void "Resolves workspace file and returns correct details"() {
		given:
		service.doInit()
		Path theWorkspace = Paths.get("/path/to/workspaces/workspace123/")
		Path theFile = theWorkspace.resolve("path/to/file.txt")
		
		and:
		io.notExists(theWorkspace) >> false
		io.isDirectory(theWorkspace) >> true
		io.readString(theWorkspace.resolve(".workspace")) >> "workspace123"

		when:
		Optional<WorkspaceFile> result = service.getWorkspaceFile("workspace123", "path/to/file.txt")

		then:
		1 * io.exists(theFile) >> true
		1 * io.probeContentType(theFile) >> "text/plain"
		1 * io.size(theFile) >> 123

		and:
		with(result.get()) { file ->
			file.path() == theFile
			file.size() == 123
			file.contentType() == "text/plain"
		}
	}

	void "Lists workspace files and returns correct details"() {
		given:
		service.doInit()
		Path theWorkspace = Paths.get("/path/to/workspaces/workspace123/")

		and:
		io.notExists(theWorkspace) >> false
		io.isDirectory(theWorkspace) >> true
		io.readString(theWorkspace.resolve(".workspace")) >> "workspace123"
		
		and:
		Path workspaceFile1 = theWorkspace.resolve("outputs/file1.txt")
		Path workspaceFile2 = theWorkspace.resolve("outputs/file2.txt")
		Path workspaceFile3 = theWorkspace.resolve("outputs/.otherfile")

		when:
		List<WorkspaceFile> result = service.getWorkspaceFiles("workspace123").toList()

		then:
		1 * io.walk(theWorkspace) >> {
			Stream.of(workspaceFile1, workspaceFile2, workspaceFile3)
		}
		3 * io.isRegularFile(_) >> true
		2 * io.probeContentType(_) >> "text/plain"
		2 * io.size(_) >> 123
		
		and:
		result.size() == 2
		result.contains(new WorkspaceFile("text/plain", 123, workspaceFile1))
		result.contains(new WorkspaceFile("text/plain", 123, workspaceFile2))
	}
}
