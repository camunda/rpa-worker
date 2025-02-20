package io.camunda.rpa.worker.zeebe;

import feign.Param;
import feign.RequestLine;
import reactor.core.publisher.Mono;

interface ResourceClient {
	@RequestLine("GET /resources/{resourceKey}/content")
	Mono<RpaResource> getRpaResource(
			@Param("resourceKey") String resourceKey);
}
