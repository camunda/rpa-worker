package io.camunda.rpa.worker

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Duration

trait PublisherUtils {

	private final static Duration TIMEOUT = Duration.ofSeconds(3)

	public <T> T block(Mono<T> mono) {
		return mono.block(TIMEOUT)
	}
	
	public <T> List<T> block(Flux<T> flux) {
		return flux.collectList().block(TIMEOUT)
	}
}