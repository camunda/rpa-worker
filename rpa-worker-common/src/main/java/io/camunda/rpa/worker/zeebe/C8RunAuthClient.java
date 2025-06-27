package io.camunda.rpa.worker.zeebe;

import feign.Param;
import feign.RequestLine;
import reactivefeign.client.ReactiveHttpResponse;
import reactor.core.publisher.Mono;

public interface C8RunAuthClient {
	@RequestLine("POST /api/login?username={username}&password={password}")
	Mono<ReactiveHttpResponse<Mono<Void>>> login(
			@Param("username") String username, 
			@Param("password") String password);
}
