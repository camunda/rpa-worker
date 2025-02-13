package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.check.StartupCheck;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.common.AuthProperties;
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!ftest")
class ZeebeStartupCheck implements StartupCheck<ZeebeReadyEvent> {
	
	private static final int NUM_ATTEMPTS = 3;
	
	private final ZeebeClient zeebeClient;
	private final CamundaClientProperties camundaClientProperties;

	@Override
	public Mono<ZeebeReadyEvent> check() {
		
		if( ! camundaClientProperties.getZeebe().getEnabled())
			return Mono.<ZeebeReadyEvent>empty()
					.doOnSubscribe(_ -> log.atInfo().log("Zeebe check skipped as client is not enabled"));
		
		return Mono.fromCompletionStage(() -> zeebeClient.newTopologyRequest().send())
				.retryWhen(Retry.backoff(NUM_ATTEMPTS, Duration.ofSeconds(2)))

				.doOnError(thrown -> log.atError()
						.setCause(thrown)
						.log(("Failed to communicate with Zeebe after %s attempts, please verify all necessary configuration is provided. " +
								"If using a configuration file to configure the RPA Worker ensure it contains all required properties, " +
								"is named rpa-worker.properties, rpa-worker.yaml, application.properties, or application.yaml, and " +
								"is either in the current directory or the config/ directory. ").formatted(NUM_ATTEMPTS)))

				.map(_ -> new ZeebeReadyEvent(zeebeClient))

				.doOnSubscribe(_ -> log.atInfo()
						.kv("client-mode", camundaClientProperties.getMode())
						.kv("client-id", Optional.ofNullable(camundaClientProperties.getAuth()).map(AuthProperties::getClientId).orElse(null))
						.kv("cluster-id", camundaClientProperties.getClusterId())
						.kv("region", camundaClientProperties.getRegion())
						.kv("zeebe-grpc-address", Optional.ofNullable(camundaClientProperties.getZeebe()).map(ZeebeClientProperties::getGrpcAddress).orElse(null))
						.kv("zeebe-rest-address", Optional.ofNullable(camundaClientProperties.getZeebe()).map(ZeebeClientProperties::getRestAddress).orElse(null))
						.kv("zeebe-prefer-rest-over-grpc", Optional.ofNullable(camundaClientProperties.getZeebe()).map(ZeebeClientProperties::isPreferRestOverGrpc).orElse(null))
						.log("Checking Zeebe connection"));
	}

	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_ZEEBE_CONNECTION;
	}
}
