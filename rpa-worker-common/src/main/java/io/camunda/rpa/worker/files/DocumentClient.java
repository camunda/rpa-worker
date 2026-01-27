package io.camunda.rpa.worker.files;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@HttpExchange
public interface DocumentClient {
	
	@GetExchange("/documents/{documentId}?storeId={storeId}&contentHash={contentHash}")
	Flux<DataBuffer> getDocument(
			@PathVariable String documentId,
			@PathVariable String storeId,
			@PathVariable String contentHash);

	default Mono<ZeebeDocumentDescriptor> uploadDocument(
			MultiValueMap<String, HttpEntity<?>> data, 
			@RequestParam Map<String, String> query) {
		HttpEntity<?> metadata88 = data.remove("metadata88").getFirst();
		return doUploadDocument(data, query)
				.onErrorComplete(WebClientResponseException.BadRequest.class)
				.switchIfEmpty(Mono.defer(() -> {
					data.set("metadata", metadata88);
					return doUploadDocument(data, query);
				}));
	}

	@PostExchange(value = "/documents", headers = "Content-type: multipart/form-data")
	Mono<ZeebeDocumentDescriptor> doUploadDocument(@RequestBody MultiValueMap<String, HttpEntity<?>> data, @RequestParam Map<String, String> query);
}
