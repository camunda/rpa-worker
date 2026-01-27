package io.camunda.rpa.worker.zeebe;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

@HttpExchange
public interface AuthClient {
	
	Mono<AuthenticationResponse> authenticate(AuthenticationRequest auth);

	record AuthenticationRequest(String clientId, String clientSecret, String audience, String grantType) { }
	record AuthenticationResponse(String accessToken, int expiresIn) { }
	
	interface InternalClient {
		@PostExchange(value = "/token", contentType = "application/x-www-form-urlencoded")
		Mono<AuthenticationResponse> authenticate(@RequestBody MultiValueMap<String, Object> body);
	}
}
