package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ZeebeAuthenticationServiceSpec extends Specification implements PublisherUtils {
	
	static String audience = "SOME_AUDIENCE"

	AuthClient authClient = Mock()
	ZeebeAuthProperties authProperties = new ZeebeAuthProperties("client-id", "client-secret")

	@Subject
	ZeebeAuthenticationService service = new ZeebeAuthenticationService(authClient, authProperties)

	void "Uses cached authentication token"() {
		when:
		String token1 = block service.getAuthToken(audience)

		then:
		1 * authClient.authenticate(new AuthClient.AuthenticationRequest(
				authProperties.clientId(), 
				authProperties.clientSecret(), 
				audience, 
				"client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("the-access-token", 3600))
		
		and:
		token1 == "the-access-token"

		when:
		String token2 = block service.getAuthToken(audience)

		then:
		0 * authClient.authenticate(_)
		
		and:
		token2 == "the-access-token"
	}

	void "Refreshes cached auth token when expired"() {
		when:
		String token1 = block service.getAuthToken(audience)

		then:
		1 * authClient.authenticate(new AuthClient.AuthenticationRequest(
				authProperties.clientId(), 
				authProperties.clientSecret(),
				audience, 
				"client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("first-access-token", 0))
		
		and:
		token1 == "first-access-token"

		when:
		String token2 = block service.getAuthToken(audience)

		then:
		1 * authClient.authenticate(new AuthClient.AuthenticationRequest(
				authProperties.clientId(), 
				authProperties.clientSecret(), 
				audience, 
				"client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("second-access-token", 3600))
		
		and:
		token2 == "second-access-token"
	}
}
