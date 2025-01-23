package io.camunda.rpa.worker.check;

import org.springframework.context.ApplicationEvent;
import reactor.core.publisher.Mono;

public interface StartupCheck<E extends ApplicationEvent> {
	Mono<E> check();
	int getExitCodeForFailure();
}
