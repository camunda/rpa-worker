package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.util.HttpHeaderUtils;
import io.camunda.zeebe.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import java.net.HttpCookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
@ConditionalOnCamundaClientEnabled
public class ZeebeAuthenticationService {
	
	private final AuthClient authClient;
	private final C8RunAuthClient c8RunAuthClient;
	private final ZeebeProperties zeebeProperties;
	
	private final Duration cookieRefreshTime;
	
	private final Map<Authentication, Mono<String>> tokens = new ConcurrentHashMap<>();

	private record TokenWithAbsoluteExpiry(String token, Instant expiry) { }
	private record Authentication(String client, String clientSecret, String audience) {}

	@Autowired
	public ZeebeAuthenticationService(AuthClient authClient, C8RunAuthClient c8RunAuthClient, ZeebeProperties zeebeProperties) {
		this(authClient, c8RunAuthClient, zeebeProperties, Duration.ofSeconds(90));
	}

	public Mono<String> getAuthToken(String client, String clientSecret, String audience) {
		Authentication authentication = new Authentication(client, clientSecret, audience);
		return tokens.computeIfAbsent(authentication, a -> switch(zeebeProperties.authMethod()) {
			case TOKEN -> createAuthenticator(a);
			case COOKIE -> createCookieAuthenticator(a);
			case BASIC -> Mono.just(HttpHeaderUtils.basicAuth(a.client(), a.clientSecret()));
			case NONE -> Mono.empty();
		});
	}

	private Mono<String> createAuthenticator(Authentication authentication) {
		return doCreateAuthenticator(() -> authClient.authenticate(new AuthClient.AuthenticationRequest(
								authentication.client(),
								authentication.clientSecret(),
								authentication.audience(),
								"client_credentials"))

						.doOnSubscribe(_ -> log.atInfo()
								.kv("audience", authentication.audience())
								.log("Refreshing auth token")),

				resp -> new TokenWithAbsoluteExpiry(
						resp.accessToken(),
						Instant.now().minusSeconds(60).plusSeconds(resp.expiresIn())));
	}

	private Mono<String> createCookieAuthenticator(Authentication authentication) {
		return doCreateAuthenticator(() -> c8RunAuthClient.login(authentication.client(), authentication.clientSecret())

						.doOnSubscribe(_ -> log.atInfo()
								.kv("username", authentication.client())
								.log("Refreshing auth cookie")),

				r -> new TokenWithAbsoluteExpiry(HttpHeaders.readOnlyHttpHeaders(MultiValueMap.fromMultiValue(r.headers()))
						.get(HttpHeaders.SET_COOKIE).stream()
						.flatMap(header -> HttpCookie.parse(header).stream())
						.collect(Collectors.toMap(HttpCookie::getName, HttpCookie::getValue))
						.get("OPERATE-SESSION"),
						Instant.now().plus(cookieRefreshTime)));
	}
	
	private <R> Mono<String> doCreateAuthenticator(Supplier<Mono<R>> fetcher, Function<R, TokenWithAbsoluteExpiry> handler) {
		return Mono.defer(fetcher)
				.map(handler)
				.cacheInvalidateIf(token -> token.expiry().isBefore(Instant.now()))
				.map(TokenWithAbsoluteExpiry::token);
	}
}
