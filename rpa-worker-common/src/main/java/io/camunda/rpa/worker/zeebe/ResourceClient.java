package io.camunda.rpa.worker.zeebe;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Flux;

@HttpExchange
interface ResourceClient {
	@GetExchange("/resources/{resourceKey}/content")
	Flux<DataBuffer> getRpaResource(@PathVariable String resourceKey);
}
