package io.camunda.rpa.worker.operate;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@RequiredArgsConstructor
@Slf4j
public class RetryingOperateClientWrapper implements OperateClient {
	
	private static final Retry retrySpec = Retry
			.backoff(3, Duration.ofSeconds(1))
			.filter(thrown -> ! (thrown instanceof FeignException.FeignClientException));
	
	private final OperateClient delegate;

	@Override
	public Mono<GetProcessInstanceResponse> getProcessInstance(long key) {
		return delegate.getProcessInstance(key).retryWhen(retrySpec);
	}

	@Override
	public Mono<GetIncidentsResponse> getIncidents87(GetIncidentsRequest request) {
		return delegate.getIncidents87(request).retryWhen(retrySpec);
	}

	@Override
	public Mono<GetIncidentsResponse> getIncidents88a(long key, GetIncidentsRequest request) {
		return delegate.getIncidents88a(key, request).retryWhen(retrySpec);
	}

	@Override
	public Mono<GetIncidentsResponse> getIncidents88b(GetIncidentsRequest88b request) {
		return delegate.getIncidents88b(request).retryWhen(retrySpec);
	}

	@Override
	public Mono<GetVariablesResponse> doGetVariables(GetVariablesRequest request) {
		return delegate.doGetVariables(request).retryWhen(retrySpec);
	}

	@Override
	public Mono<GetVariablesResponse> doGetVariables88(GetVariablesRequest88 request) {
		return delegate.doGetVariables88(request).retryWhen(retrySpec);
	}
}
