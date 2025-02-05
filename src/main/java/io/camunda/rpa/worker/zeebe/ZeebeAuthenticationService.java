package io.camunda.rpa.worker.zeebe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZeebeAuthenticationService {
	
	private final AuthClient authClient;
	private final ZeebeAuthProperties zeebeAuthProperties;
	
	private final Map<String, Mono<String>> tokens = new ConcurrentHashMap<>();

	private record TokenWithAbsoluteExpiry(String token, Instant expiry) { }

	public Mono<String> getAuthToken(String audience) {
		return tokens.computeIfAbsent(audience, this::createAuthenticator);
	}

	private Mono<String> createAuthenticator(String audience) {
		return Mono.defer(() -> authClient.authenticate(new AuthClient.AuthenticationRequest(
						zeebeAuthProperties.clientId(),
						zeebeAuthProperties.clientSecret(),
						audience,
						"client_credentials"))

				.doOnSubscribe(_ -> log.atInfo()
						.kv("audience", audience)
						.log("Refreshing auth token")))

				.map(resp -> new TokenWithAbsoluteExpiry(
						resp.accessToken(),
						Instant.now().minusSeconds(60).plusSeconds(resp.expiresIn())))
				
				.cacheInvalidateIf(token -> token.expiry().isBefore(Instant.now()))
				.map(TokenWithAbsoluteExpiry::token);
	}
}
