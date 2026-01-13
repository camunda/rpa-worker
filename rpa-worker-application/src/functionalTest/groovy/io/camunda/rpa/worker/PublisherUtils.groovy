package io.camunda.rpa.worker

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.time.Duration

trait PublisherUtils {

	public <T> T block(Mono<T> mono) {
		return mono.block(subscribeTimeout)
	}
	
	public <T> List<T> block(Flux<T> flux) {
		return flux.collectList().block(subscribeTimeout)
	}
	
	public Duration getSubscribeTimeout() {
		return Duration.ofSeconds(3)
	}
}