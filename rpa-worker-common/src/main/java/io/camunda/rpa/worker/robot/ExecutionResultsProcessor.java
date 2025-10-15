package io.camunda.rpa.worker.robot;

import reactor.core.publisher.Mono;

public interface ExecutionResultsProcessor {
	Mono<ExecutionResults> withExecutionResults(ExecutionResults results);
}
