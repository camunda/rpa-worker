package io.camunda.rpa.worker.zeebe;

import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@HttpExchange
public interface AuthClient {
	
	Mono<AuthenticationResponse> authenticate(AuthenticationRequest auth);

	record AuthenticationRequest(String clientId, String clientSecret, String audience, String grantType) { }
	record AuthenticationResponse(String accessToken, int expiresIn) { }
	
	interface InternalClient {
		@PostExchange(value = "/token", headers = "Content-Type: application/x-www-form-urlencoded")
		Mono<AuthenticationResponse> authenticate(Map<String, Object> body);
	}
}
