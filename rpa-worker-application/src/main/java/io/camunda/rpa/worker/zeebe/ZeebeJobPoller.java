package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.util.LoopingListIterator;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
class ZeebeJobPoller implements ApplicationListener<ZeebeReadyEvent> {

	static final Duration JOB_POLL_TIME = Duration.ofMillis(200);

	private static final Predicate<Throwable> isGrpcShutdownNoise = thrown ->
			thrown instanceof StatusRuntimeException srex
					&& srex.getStatus().getCode() == Status.Code.UNAVAILABLE
					&& srex.getStatus().getDescription().contains("shutdown");

	private final ZeebeProperties zeebeProperties;
	private final ZeebeClient zeebeClient;
	private final ZeebeJobService zeebeJobService;
	private final Iterable<String> tagGenerator;
	
	private Disposable poller;

	@Autowired
	public ZeebeJobPoller(ZeebeProperties zeebeProperties, ZeebeClient zeebeClient, ZeebeJobService zeebeJobService) {
		this(zeebeProperties,
				zeebeClient,
				zeebeJobService,
				defaultTagGenerator(zeebeProperties.workerTags(), zeebeProperties.rpaTaskPrefix()));
	}

	private static Iterable<String> defaultTagGenerator(Set<String> workerTags, String taskPrefix) {
		return () -> new LoopingListIterator<>(workerTags.stream()
				.map(t -> taskPrefix + t)
				.collect(Collectors.toList()));
	}

	@Override
	public void onApplicationEvent(ZeebeReadyEvent ignored) {
		init();
	}

	void init() {
		log.atInfo()
				.kv("workerTags", zeebeProperties.workerTags())
				.log("Accepting Zeebe jobs for tags");

		poller = Flux.fromIterable(tagGenerator)
				.flatMap(jobType -> Mono.defer(() -> Mono.fromCompletionStage(

										zeebeClient.newActivateJobsCommand()
												.jobType(jobType)
												.maxJobsToActivate(1)
												.requestTimeout(JOB_POLL_TIME)
												.send()))

								.onErrorReturn(isGrpcShutdownNoise, Collections::emptyList)

								.doOnError(thrown -> log.atError()
										.setCause(thrown)
										.log("Error polling Zeebe for jobs"))
								.onErrorReturn(Collections::emptyList)

								.doOnSubscribe(_ -> log.atDebug()
										.kv("jobType", jobType)
										.log("Polling for job"))

								.flatMapIterable(ActivateJobsResponse::getJobs)
								.flatMap(zeebeJobService::handleJob).onErrorComplete(),

						zeebeProperties.maxConcurrentJobs())
				.subscribe();
	}
	
	@PreDestroy
	void shutdown() {
		poller.dispose();
	}
}
