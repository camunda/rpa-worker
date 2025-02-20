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
	
	private final Map<Authentication, Mono<String>> tokens = new ConcurrentHashMap<>();

	private record TokenWithAbsoluteExpiry(String token, Instant expiry) { }
	private record Authentication(String client, String clientSecret, String audience) {}

	public Mono<String> getAuthToken(String client, String clientSecret, String audience) {
		Authentication authentication = new Authentication(client, clientSecret, audience);
		return tokens.computeIfAbsent(authentication, this::createAuthenticator);
	}

	private Mono<String> createAuthenticator(Authentication authentication) {
		return Mono.defer(() -> authClient.authenticate(new AuthClient.AuthenticationRequest(
								authentication.client(),
								authentication.clientSecret(),
								authentication.audience(),
								"client_credentials"))

						.doOnSubscribe(_ -> log.atInfo()
								.kv("audience", authentication.audience())
								.log("Refreshing auth token")))

				.map(resp -> new TokenWithAbsoluteExpiry(
						resp.accessToken(),
						Instant.now().minusSeconds(60).plusSeconds(resp.expiresIn())))

				.cacheInvalidateIf(token -> token.expiry().isBefore(Instant.now()))
				.map(TokenWithAbsoluteExpiry::token);
	}
}
