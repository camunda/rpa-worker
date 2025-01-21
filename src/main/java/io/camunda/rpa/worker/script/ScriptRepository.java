package io.camunda.rpa.worker.script;

import reactor.core.publisher.Mono;

public interface ScriptRepository {
	Mono<RobotScript> findById(String id);
	Mono<RobotScript> save(RobotScript robotScript);
}
