package io.camunda.rpa.worker.workspace.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.workspace.WorkspaceVariablesManager
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class WorkspaceVariablesControllerSpec extends Specification implements PublisherUtils {
	
	private static final Map<String, Object> someVars = [foo: 'bar']
	
	WorkspaceVariablesManager workspaceVariablesManager = Mock()
	
	@Subject
	WorkspaceVariablesController controller = new WorkspaceVariablesController(workspaceVariablesManager)

	void "Returns not found when Workspace does not exist"() {
		when:
		ResponseEntity<?> r = block controller.attachVariables("abc123", new AttachVariablesRequest(someVars))
		
		then:
		1 * workspaceVariablesManager.attachVariables("abc123", someVars) >> Mono.error(new NoSuchElementException())
		
		and:
		r.statusCode == HttpStatus.NOT_FOUND
	}

	void "Returns OK when Workspace exists"() {
		when:
		ResponseEntity<?> r = block controller.attachVariables("abc123", new AttachVariablesRequest(someVars))

		then:
		1 * workspaceVariablesManager.attachVariables("abc123", someVars) >> Mono.empty()

		and:
		r.statusCode == HttpStatus.OK
	}
}
