package io.camunda.rpa.worker.zeebe;

import feign.Param;
import feign.RequestLine;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

interface ResourceClient {
	@RequestLine("GET /resources/{resourceKey}/content")
	Flux<DataBuffer> getRpaResource(
			@Param("resourceKey") String resourceKey);
}
