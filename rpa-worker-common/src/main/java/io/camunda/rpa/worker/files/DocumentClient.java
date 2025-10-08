package io.camunda.rpa.worker.files;

import feign.FeignException;
import feign.Headers;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface DocumentClient {
	
	@RequestLine("GET /documents/{documentId}?storeId={storeId}&contentHash={contentHash}")
	Flux<DataBuffer> getDocument(
			@Param("documentId") String documentId, 
			@Param("storeId") String storeId, 
			@Param("contentHash") String contentHash);

	default Mono<ZeebeDocumentDescriptor> uploadDocument(
			MultiValueMap<String, HttpEntity<?>> data, 
			@QueryMap Map<String, String> query) {
		HttpEntity<?> metadata88 = data.remove("metadata88").getFirst();
		return doUploadDocument(data, query)
				.onErrorComplete(FeignException.BadRequest.class)
				.switchIfEmpty(Mono.defer(() -> {
					data.set("metadata", metadata88);
					return doUploadDocument(data, query);
				}));
	}

	@RequestLine("POST /documents")
	@Headers("Content-type: multipart/form-data")
	Mono<ZeebeDocumentDescriptor> doUploadDocument(MultiValueMap<String, HttpEntity<?>> data, @QueryMap Map<String, String> query);
}
