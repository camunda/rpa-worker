package io.camunda.rpa.worker.workspace

import io.camunda.rpa.worker.PublisherUtils
import spock.lang.Specification
import spock.lang.Subject

class WorkspaceVariablesManagerSpec extends Specification implements PublisherUtils {
	
	@Subject
	WorkspaceVariablesManager manager = new WorkspaceVariablesManager()
	
	Workspace aWorkspace = new Workspace("abc123", null)
	
	void "Creates empty variables when Robot execution starts"() {
		when:
		manager.beforeScriptExecution(aWorkspace, null)
		
		then:
		manager.getVariables(aWorkspace.id()) == [:]
	}

	void "Clears variables when Robot execution finishes"() {
		given:
		manager.beforeScriptExecution(aWorkspace, null)
		
		when:
		manager.afterRobotExecution(aWorkspace)

		then:
		manager.getVariables(aWorkspace.id()) == null
	}
	
	void "Returns error for Workspace ID which has no variables"() {
		when:
		block manager.attachVariables("fake-workspace", [:])
		
		then:
		thrown(NoSuchElementException)
	}
	
	void "Returns attached variables"() {
		given:
		manager.beforeScriptExecution(aWorkspace, null)
		
		and:
		block manager.attachVariables(aWorkspace.id(), [foo: 'bar'])

		when:
		Map<String, Object> r = manager.getVariables(aWorkspace.id())
		
		then:
		r == [foo: 'bar']
	}
}
