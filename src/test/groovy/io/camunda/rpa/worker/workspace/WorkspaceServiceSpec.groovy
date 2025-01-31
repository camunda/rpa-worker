package io.camunda.rpa.worker.workspace

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class WorkspaceServiceSpec extends Specification implements PublisherUtils {
	
	IO io = Mock()
	
	@Subject
	WorkspaceService service = new WorkspaceService(io)

	void "Immediately deletes directory"() {
		given:
		Path aPath = Stub()

		when:
		block service.deleteWorkspace(aPath)
		
		then:
		1 * io.deleteDirectoryRecursively(aPath)
	}
	
	void "Defers deletion of directory until next"() {
		given:
		Path path1 = Stub()
		Path path2 = Stub()
		Path path3 = Stub()

		when:
		block service.preserveLast(path1)

		then:
		0 * io._(*_)
		
		when:
		block service.preserveLast(path2)
		
		then:
		1 * io.deleteDirectoryRecursively(path1)
		0 * io._(*_)
		
		when:
		block service.preserveLast(path3)

		then:
		1 * io.deleteDirectoryRecursively(path2)
		0 * io._(*_)
	}
}
