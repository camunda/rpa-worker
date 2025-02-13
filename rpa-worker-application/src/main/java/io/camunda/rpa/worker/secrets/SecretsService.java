package io.camunda.rpa.worker.secrets;

import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretsService {
	
	
	private final SecretsClient secretsClient;
	private final ZeebeAuthenticationService zeebeAuthenticationService;
	private final SecretsClientProperties secretsClientProperties;
	
	public Mono<Map<String, String>> getSecrets() {
		return zeebeAuthenticationService.getAuthToken(secretsClientProperties.tokenAudience())
				.flatMap(secretsClient::getSecrets);
	}
}
