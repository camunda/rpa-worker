package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.zeebe.ZeebeAuthenticationService
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class SecretsServiceSpec extends Specification implements PublisherUtils {
	
	SecretsClient secretsClient = Mock()
	ZeebeAuthenticationService zeebeAuthService = Mock()
	
	@Subject
	SecretsService service = new SecretsService(secretsClient, zeebeAuthService)
	
	void "Authenticates and fetches secrets"() {
		when:
		Map<String, Object> map = block service.getSecrets()
		
		then:
		1 * zeebeAuthService.getAuthToken(SecretsService.SECRETS_TOKEN_AUDIENCE) >> Mono.just("the-access-token")
		1 * secretsClient.getSecrets("the-access-token") >> Mono.just([secretVar: 'secret-value'])
		
		and:
		map == [secretVar: 'secret-value']
	}

}
