package io.camunda.rpa.worker.zeebe;

import feign.Headers;
import feign.RequestLine;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface AuthClient {
	
	Mono<AuthenticationResponse> authenticate(AuthenticationRequest auth);

	record AuthenticationRequest(String clientId, String clientSecret, String audience, String grantType) { }
	record AuthenticationResponse(String accessToken, int expiresIn) { }
	
	interface InternalClient {
		@RequestLine("POST /token")
		@Headers("Content-Type: application/x-www-form-urlencoded")
		Mono<AuthenticationResponse> authenticate(Map<String, Object> body);
	}
}
