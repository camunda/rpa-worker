package io.camunda.rpa.worker.api;

import io.camunda.rpa.worker.zeebe.ZeebeClientStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StubbedResponseGenerator {
	
	private final ZeebeClientStatus zeebeClientStatus;

	public Mono<ResponseEntity<?>> stubbedResponse(
			String target,
			String action,
			Object request) {

		if (zeebeClientStatus.isZeebeClientEnabled())
			return Mono.empty();

		return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
				.body(StubbedResponse.builder()
						.target(target)
						.action(action)
						.request(request)
						.build()));
	}
}
