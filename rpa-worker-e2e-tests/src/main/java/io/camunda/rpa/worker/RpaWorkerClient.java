package io.camunda.rpa.worker;

import io.camunda.rpa.worker.script.api.DeployScriptRequest;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

@HttpExchange
public interface RpaWorkerClient {
	@PostExchange("/script/deploy")
	Mono<Void> deployScript(DeployScriptRequest request);
}
