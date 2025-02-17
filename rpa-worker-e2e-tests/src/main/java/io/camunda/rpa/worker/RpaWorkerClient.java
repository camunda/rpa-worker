package io.camunda.rpa.worker;

import feign.RequestLine;
import io.camunda.rpa.worker.script.api.DeployScriptRequest;
import reactor.core.publisher.Mono;

public interface RpaWorkerClient {
	@RequestLine("POST /script/deploy")
	Mono<Void> deployScript(DeployScriptRequest request);
}
