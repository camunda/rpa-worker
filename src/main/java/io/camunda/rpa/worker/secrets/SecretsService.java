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
	
	static final String SECRETS_TOKEN_AUDIENCE = "secrets.camunda.io";
	
	private final SecretsClient secretsClient;
	private final ZeebeAuthenticationService zeebeAuthenticationService;
	
	public Mono<Map<String, String>> getSecrets() {
		return zeebeAuthenticationService.getAuthToken(SECRETS_TOKEN_AUDIENCE)
				.flatMap(secretsClient::getSecrets);
	}
}
