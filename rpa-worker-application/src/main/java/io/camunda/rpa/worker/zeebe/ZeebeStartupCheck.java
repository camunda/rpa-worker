package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.check.StartupCheck;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.common.AuthProperties;
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties;
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
	
	private final ObjectProvider<ZeebeClient> zeebeClient;
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
						.kv("client-id", Optional.ofNullable(camundaClientProperties.getObject().getAuth()).map(AuthProperties::getClientId).orElse(null))
						.kv("cluster-id", camundaClientProperties.getObject().getClusterId())
						.kv("region", camundaClientProperties.getObject().getRegion())
						.kv("zeebe-grpc-address", Optional.ofNullable(camundaClientProperties.getObject().getZeebe()).map(ZeebeClientProperties::getGrpcAddress).orElse(null))
						.kv("zeebe-rest-address", Optional.ofNullable(camundaClientProperties.getObject().getZeebe()).map(ZeebeClientProperties::getRestAddress).orElse(null))
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
