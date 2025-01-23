package io.camunda.rpa.worker.secrets;

import feign.RequestLine;
import reactor.core.publisher.Mono;

interface AuthClient {
	@RequestLine("POST /oauth/token")
	Mono<AuthenticationResponse> authenticate(AuthenticationRequest auth);
	
	record AuthenticationRequest(String clientId, String clientSecret, String audience, String grantType) { }
	record AuthenticationResponse(String accessToken, int expiresIn) { }
}
