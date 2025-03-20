package io.camunda.rpa.worker.workspace;

import io.camunda.rpa.worker.robot.RobotExecutionListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class WorkspaceVariablesManager implements RobotExecutionListener {

	private final Map<String, Map<String, Object>> variablesByWorkspace = new ConcurrentHashMap<>();
	
	public Mono<Void> attachVariables(String workspaceId, Map<String, Object> variables) {
		if( ! variablesByWorkspace.containsKey(workspaceId))
			return Mono.error(new NoSuchElementException());
		
		variablesByWorkspace.get(workspaceId).putAll(variables);
		
		return Mono.empty();
	}

	public Map<String, Object> getVariables(String workspaceId) {
		return variablesByWorkspace.get(workspaceId);
	}

	@Override
	public void beforeScriptExecution(Workspace workspace, Duration timeout) {
		variablesByWorkspace.put(workspace.id(), new ConcurrentHashMap<>());
	}

	@Override
	public void afterRobotExecution(Workspace workspace) {
		variablesByWorkspace.remove(workspace.id());
	}
}
