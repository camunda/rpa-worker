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
		createTempDirectory(_) >> Paths.get("/path/to/workspaces/").toAbsolutePath()
	}
	
	@Subject
	WorkspaceService service = new WorkspaceService(io)
	
	Path workspacePath = Paths.get("/path/to/workspaces/workspace123456/").toAbsolutePath()
	Workspace theWorkspace = new Workspace("workspace123456", workspacePath)

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

		when:
		Workspace workspace = service.createWorkspace()
		
		then:
		1 * io.createTempDirectory(rootDir, "workspace") >> workspacePath
		1 * io.writeString(workspacePath.resolve(".workspace"), "workspace123456", StandardOpenOption.CREATE_NEW)
		
		and:
		workspace.path() == workspacePath
	}
	
	void "Returns workspace by ID"() {
		given:
		service.doInit()
		
		and:
		io.createTempDirectory(_, "workspace") >> workspacePath
		Workspace created = service.createWorkspace()
		
		when:
		Optional<Workspace> workspace = service.getById(created.id())
		
		then:
		1 * io.exists(workspacePath) >> true
		1 * io.isDirectory(workspacePath) >> true
		
		and:
		workspace.get().path() == workspacePath
	}
	
	void "Returns empty when workspace is in incorrect location"() {
		given:
		service.doInit()

		when:
		Optional<Workspace> workspace = service.getById("../../workspace123456")

		then:
		! workspace.isPresent()
	}

	void "Returns empty when workspace does not exist"() {
		given:
		service.doInit()

		when:
		Optional<Workspace> workspace = service.getById(service.createWorkspace().id())

		then:
		1 * io.createTempDirectory(_, "workspace") >> workspacePath
		1 * io.exists(workspacePath) >> false

		and:
		! workspace.isPresent()

		when:
		Optional<Workspace> workspace2 = service.getById(service.createWorkspace().id())

		then:
		1 * io.createTempDirectory(_, "workspace") >> workspacePath
		1 * io.exists(workspacePath) >> true
		1 * io.isDirectory(workspacePath) >> false

		and:
		! workspace2.isPresent()
	}
	
	void "Resolves workspace file and returns correct details"() {
		given:
		service.doInit()
		Path theFile = workspacePath.resolve("path/to/file.txt")
		io.createTempDirectory(_, "workspace") >> workspacePath
		Workspace workspace = service.createWorkspace()
		
		and:
		io.exists(workspacePath) >> true
		io.isDirectory(workspacePath) >> true

		when:
		Optional<WorkspaceFile> result = service.getWorkspaceFile(workspace.id(), "path/to/file.txt")

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
		io.createTempDirectory(_, "workspace") >> workspacePath
		Workspace workspace = service.createWorkspace()

		and:
		io.exists(workspacePath) >> true
		io.isDirectory(workspacePath) >> true
		io.readString(workspacePath.resolve(".workspace")) >> "workspace123456"
		
		and:
		Path workspaceFile1 = workspacePath.resolve("outputs/file1.txt")
		Path workspaceFile2 = workspacePath.resolve("outputs/file2.txt")
		Path workspaceFile3 = workspacePath.resolve("outputs/.otherfile")

		when:
		List<WorkspaceFile> result = service.getWorkspaceFiles(workspace.id()).toList()

		then:
		1 * io.walk(workspacePath) >> {
			Stream.of(workspaceFile1, workspaceFile2, workspaceFile3)
		}
		3 * io.isRegularFile(_) >> true
		2 * io.probeContentType(_) >> "text/plain"
		2 * io.size(_) >> 123
		
		and:
		result.size() == 2
		result.contains(new WorkspaceFile(workspace, "text/plain", 123, workspaceFile1))
		result.contains(new WorkspaceFile(workspace, "text/plain", 123, workspaceFile2))
	}
}
