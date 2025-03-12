package io.camunda.rpa.worker.workspace

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class WorkspaceCleanupServiceSpec extends Specification implements PublisherUtils {
	
	IO io = Mock()
	
	@Subject
	WorkspaceCleanupService service = new WorkspaceCleanupService(io)

	void "Immediately deletes directory"() {
		given:
		Path aPath = Stub()

		when:
		block service.deleteWorkspace(new Workspace(null, aPath))
		
		then:
		1 * io.deleteDirectoryRecursively(aPath)
	}
	
	void "Defers deletion of directory until next"() {
		given:
		Path path1 = Stub()
		Path path2 = Stub()
		Path path3 = Stub()

		when:
		block service.preserveLast(new Workspace(null, path1))

		then:
		0 * io._(*_)
		
		when:
		block service.preserveLast(new Workspace(null, path2))
		
		then:
		1 * io.deleteDirectoryRecursively(path1)
		0 * io._(*_)
		
		when:
		block service.preserveLast(new Workspace(null, path3))

		then:
		1 * io.deleteDirectoryRecursively(path2)
		0 * io._(*_)
	}
	
	void "Defers deletion of workspace in named queue until next"() {
		given:
		Path path1 = Stub()
		Path path2 = Stub()
		Path path3 = Stub()
		Path path4 = Stub()

		when:
		block service.preserveLast(new Workspace(null, path1, "key-1"))
		block service.preserveLast(new Workspace(null, path2, "key-2"))
		block service.preserveLast(new Workspace(null, path3, "key-2"))
		
		then:
		0 * io.deleteDirectoryRecursively(path1)
		1 * io.deleteDirectoryRecursively(path2)
		0 * io.deleteDirectoryRecursively(path3)
		
		when:
		block service.preserveLast(new Workspace(null, path4, "key-1"))
		
		then:
		1 * io.deleteDirectoryRecursively(path1)
	}
}
