package io.camunda.rpa.worker.files;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpEntity;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class RetryingDocumentClientWrapper implements DocumentClient {
	
	private static final Retry retrySpec = Retry
			.backoff(3, Duration.ofSeconds(1))
			.filter(thrown -> ! (thrown instanceof FeignException.FeignClientException));
	
	private final DocumentClient delegate;
	
	@Override
	public Flux<DataBuffer> getDocument(String documentId, String storeId, String contentHash) {
		return delegate.getDocument(documentId, storeId, contentHash)
				.retryWhen(retrySpec);
	}

	@Override
	public Mono<ZeebeDocumentDescriptor> doUploadDocument(MultiValueMap<String, HttpEntity<?>> data, Map<String, String> query) {
		return delegate.doUploadDocument(data, query)
				.retryWhen(retrySpec);
	}
}
