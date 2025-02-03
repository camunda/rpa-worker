package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.check.StartupCheck;
import io.camunda.zeebe.client.ZeebeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!ftest")
class ZeebeStartupCheck implements StartupCheck<ZeebeReadyEvent> {
	
	private static final int NUM_ATTEMPTS = 3;
	
	private final ZeebeClient zeebeClient;

	@Override
	public Mono<ZeebeReadyEvent> check() {
		return Mono.fromCompletionStage(() -> zeebeClient.newTopologyRequest().send())
				.retryWhen(Retry.backoff(NUM_ATTEMPTS, Duration.ofSeconds(2)))

				.doOnError(thrown -> log.atError()
						.setCause(thrown)
						.log(("Failed to communicate with Zeebe after %s attempts, please verify all necessary configuration is provided. " +
								"If using a configuration file to configure the RPA Worker ensure it contains all required properties, " +
								"is named rpa-worker.properties, rpa-worker.yaml, application.properties, or application.yaml, and " +
								"is either in the current directory or the config/ directory. ").formatted(NUM_ATTEMPTS)))

				.map(_ -> new ZeebeReadyEvent(zeebeClient));
	}

	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_ZEEBE_CONNECTION;
	}
}
