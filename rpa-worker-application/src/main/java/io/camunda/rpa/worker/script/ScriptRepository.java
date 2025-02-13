package io.camunda.rpa.worker.script;

import feign.FeignException;
import reactor.core.publisher.Mono;

public interface ScriptRepository {
	
	String getKey();
	
	Mono<RobotScript> findById(String id);
	Mono<RobotScript> save(RobotScript robotScript);

	default Mono<RobotScript> getById(String id) {
		return findById(id)
				.onErrorComplete(FeignException.NotFound.class)
				.switchIfEmpty(Mono.error(new ScriptNotFoundException(id)));
	}
}
