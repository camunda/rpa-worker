package io.camunda.rpa.worker.script;

import reactor.core.publisher.Mono;

public interface ScriptRepository {
	Mono<RobotScript> findById(String id);
	Mono<RobotScript> save(RobotScript robotScript);

	default Mono<RobotScript> getById(String id) {
		return findById(id)
				.switchIfEmpty(Mono.error(new ScriptNotFoundException(id)));
	}
}
