package io.camunda.rpa.worker.zeebe;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import reactor.core.publisher.Mono;

interface ResourceClient {
	@RequestLine("GET /resources/{resourceKey}/content")
	@Headers("Authorization: Bearer {authToken}")
	Mono<RpaResource> getRpaResource(
			@Param("authToken") String authToken,
			@Param("resourceKey") String resourceKey);
}
