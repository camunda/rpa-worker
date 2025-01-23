package io.camunda.rpa.worker.secrets;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretsService {
	
	private final AuthClient authClient;
	private final ZeebeAuthProperties zeebeAuthProperties;
	private final SecretsClient secretsClient;
	
	private Mono<String> authToken;
	
	private record TokenWithAbsoluteExpiry(String token, Instant expiry) {}
	
	@PostConstruct
	SecretsService init() {
		authToken = authClient.authenticate(new AuthClient.AuthenticationRequest(
						zeebeAuthProperties.clientId(),
						zeebeAuthProperties.clientSecret(),
						"secrets.camunda.io",
						"client_credentials"))
				.doOnSubscribe(_ -> log.atInfo().log("Refreshing auth token for secrets"))
				.map(resp -> new TokenWithAbsoluteExpiry(resp.accessToken(),
						Instant.now().minusSeconds(60).plusSeconds(resp.expiresIn())))
				.cacheInvalidateIf(token -> token.expiry().isBefore(Instant.now()))
				.map(TokenWithAbsoluteExpiry::token);

		return this;
	}
	
	public Mono<Map<String, String>> getSecrets() {
		return authToken.flatMap(secretsClient::getSecrets);
	}
}
