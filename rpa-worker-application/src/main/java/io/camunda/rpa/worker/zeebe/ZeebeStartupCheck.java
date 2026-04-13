package io.camunda.rpa.worker.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.properties.CamundaClientAuthProperties;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.check.StartupCheck;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
	
	private final ObjectProvider<CamundaClient> zeebeClient;
	private final ObjectProvider<CamundaClientProperties> camundaClientProperties;
	private final ZeebeClientStatus zeebeClientStatus;

	@Override
	public Mono<ZeebeReadyEvent> check() {
		
		if( ! zeebeClientStatus.isZeebeClientEnabled())
			return Mono.<ZeebeReadyEvent>empty()
					.doOnSubscribe(_ -> log.atInfo().log("Zeebe check skipped as client is not enabled"));
		
		return Mono.fromCompletionStage(() -> zeebeClient.getObject().newTopologyRequest().send())
				.retryWhen(Retry.backoff(NUM_ATTEMPTS, Duration.ofSeconds(2)))
				
				.doOnSubscribe(_ -> log.atInfo()
						.kv("client-mode", camundaClientProperties.getObject().getMode())
						.kv("client-id", Optional.ofNullable(camundaClientProperties.getObject().getAuth()).map(CamundaClientAuthProperties::getClientId).orElse(null))
						.kv("cluster-id", camundaClientProperties.getObject().getCloud().getClusterId())
						.kv("region", camundaClientProperties.getObject().getCloud().getRegion())
						.kv("zeebe-grpc-address", Try.of(camundaClientProperties::getObject).map(CamundaClientProperties::getGrpcAddress).getOrNull())
						.kv("zeebe-rest-address", Try.of(camundaClientProperties::getObject).map(CamundaClientProperties::getRestAddress).getOrNull())
						.log("Checking Zeebe connection"))

				.doOnError(thrown -> log.atError()
						.setCause(thrown)
						.log(("Failed to communicate with Zeebe after %s attempts, please verify all necessary configuration is provided. " +
								"If using a configuration file to configure the RPA Worker ensure it contains all required properties, " +
								"is named rpa-worker.properties, rpa-worker.yaml, application.properties, or application.yaml, and " +
								"is either in the current directory or the config/ directory. ").formatted(NUM_ATTEMPTS)))

				.map(_ -> new ZeebeReadyEvent(zeebeClient.getObject()));
	}

	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_ZEEBE_CONNECTION;
	}
}
