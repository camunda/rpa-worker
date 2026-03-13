package io.camunda.rpa.worker.zeebe;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

@HttpExchange
public interface C8RunAuthClient {
	@PostExchange("/api/login?username={username}&password={password}")
	Mono<ResponseEntity<Void>> login(@PathVariable String username, @PathVariable String password);
}
