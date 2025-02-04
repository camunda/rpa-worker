package io.camunda.rpa.worker.files;

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
	
	@RequestLine("GET /documents/{documentId}")
	@Headers("Authorization: Bearer {authToken}")
	Flux<DataBuffer> getDocument(
			@Param("authToken") String authToken,
			@Param("documentId") String documentId,
			@QueryMap Map<String, String> query);

	@RequestLine("POST /documents")
	@Headers({"Content-type: multipart/form-data", "Authorization: Bearer {authToken}"})
	Mono<ZeebeDocumentDescriptor> uploadDocument(
			@Param("authToken") String authToken,
			MultiValueMap<String, HttpEntity<?>> data, 
			@QueryMap Map<String, String> query);
}
