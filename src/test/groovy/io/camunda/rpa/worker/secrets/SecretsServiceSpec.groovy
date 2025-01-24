package io.camunda.rpa.worker.secrets


import io.camunda.rpa.worker.PublisherUtils
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class SecretsServiceSpec extends Specification implements PublisherUtils {
	
	AuthClient authClient = Mock()
	ZeebeAuthProperties authProperties = new ZeebeAuthProperties("client-id", "client-secret")
	SecretsClient secretsClient = Mock()
	
	@Subject
	SecretsService service = new SecretsService(authClient, authProperties, secretsClient)
	
	void "Authenticates and fetches secrets"() {
		given:
		authClient.authenticate(new AuthClient.AuthenticationRequest("client-id", "client-secret", "secrets.camunda.io", "client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("the-access-token", 3600))
		secretsClient.getSecrets("the-access-token") >> Mono.just([secretVar: 'secret-value'])
		service.init()

		when:
		Map<String, Object> map = block service.getSecrets()
		
		then:
		map == [secretVar: 'secret-value']
	}
	
	void "Uses cached authentication token"() {
		given:
		secretsClient.getSecrets("the-access-token") >> Mono.just([secretVar: 'secret-value'])

		when:
		service.init()
		block service.getSecrets()

		then:
		1 * authClient.authenticate(new AuthClient.AuthenticationRequest("client-id", "client-secret", "secrets.camunda.io", "client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("the-access-token", 3600))
		
		when:
		block service.getSecrets()
		
		then:
		0 * authClient.authenticate(_)
	}

	void "Refreshes cached auth token when expired"() {
		when:
		service.init()
		block service.getSecrets()

		then:
		1 * authClient.authenticate(new AuthClient.AuthenticationRequest("client-id", "client-secret", "secrets.camunda.io", "client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("first-access-token", 0))
		1 * secretsClient.getSecrets("first-access-token") >> Mono.just([secretVar: 'secret-value'])

		when:
		block service.getSecrets()

		then:
		1 * authClient.authenticate(new AuthClient.AuthenticationRequest("client-id", "client-secret", "secrets.camunda.io", "client_credentials")) >> Mono.just(new AuthClient.AuthenticationResponse("second-access-token", 3600))
		1 * secretsClient.getSecrets("second-access-token") >> Mono.just([secretVar: 'secret-value'])
	}

}
