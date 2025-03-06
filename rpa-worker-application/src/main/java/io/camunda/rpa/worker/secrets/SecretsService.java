package io.camunda.rpa.worker.secrets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretsService {
	
	private final SecretsClient secretsClient;
	private final SecretsClientProperties secretsClientProperties;
	
	public Mono<Map<String, String>> getSecrets() {
		if( ! isSecretsApiEnabled())
			return Mono.just(Collections.emptyMap());
		
		return secretsClient.getSecrets()
				.doOnError(thrown -> log.atError()
						.setCause(thrown)
						.log("Error occurred fetching secrets"))
				.onErrorReturn(Collections.emptyMap());
	}
	
	private boolean isSecretsApiEnabled() {
		return secretsClientProperties.secretsEndpoint() != null;
	}
}
