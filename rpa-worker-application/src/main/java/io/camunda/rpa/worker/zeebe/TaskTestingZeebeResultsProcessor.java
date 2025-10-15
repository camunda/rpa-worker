package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.files.FilesService;
import io.camunda.rpa.worker.robot.ExecutionResults;
import io.camunda.rpa.worker.robot.ExecutionResultsProcessor;
import io.camunda.rpa.worker.util.MoreCollectors;
import io.camunda.rpa.worker.workspace.WorkspaceService;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
class TaskTestingZeebeResultsProcessor implements ExecutionResultsProcessor {
	
	// TODO: DO NOT MERGE! These are not final yet
	static final String TASK_TESTING_VARIABLE_NAME = "camunda::isTesting";
	static final String TASK_TESTING_LOG_OUTPUT_VARIABLE_NAME = "camunda::log";
	
	private final WorkspaceService workspaceService;
	private final FilesService filesService;
	private final Map<String, Object> inputVariables;
	private final ActivatedJob job;
	
	@Override
	public Mono<ExecutionResults> withExecutionResults(ExecutionResults results) {
		if( ! inputVariables.containsKey(TASK_TESTING_VARIABLE_NAME))
			return Mono.just(results);

		return Mono.justOrEmpty(workspaceService.getWorkspaceFile(results.workspace(), "output/main/log.html"))
				.flatMap(log ->
						filesService.uploadDocument(log,
								FilesService.toMetadata(log,
										new ZeebeJobInfo(job.getBpmnProcessId(), job.getProcessInstanceKey()))))

				.map(zdd -> results.toBuilder()
						.outputVariables(withVariable(results.outputVariables(), TASK_TESTING_LOG_OUTPUT_VARIABLE_NAME, zdd))
						.build());
	}
	
	private static <K, V> Map<K, V> withVariable(Map<K, V> map, K key, V value) {
		return Stream.concat(
						map.entrySet().stream(),
						Stream.of(Map.entry(key, value)))
				
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, MoreCollectors.MergeStrategy.rightPrecedence()));
	}
}
