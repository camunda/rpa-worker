package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.PublisherUtils
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class SecretsServiceSpec extends Specification implements PublisherUtils {
	
	SecretsClient secretsClient = Mock()

	void "Authenticates and fetches secrets"() {
		given:
		SecretsClientProperties secretsClientProperties = new SecretsClientProperties("http://secrets".toURI(), "secrets-token-audience")
		
		@Subject
		SecretsService service = new SecretsService(secretsClient, secretsClientProperties)

		when:
		Map<String, Object> map = block service.getSecrets()
		
		then:
		1 * secretsClient.getSecrets() >> Mono.just([secretVar: 'secret-value'])
		
		and:
		map == [secretVar: 'secret-value']
	}

	void "Returns empty secrets when not enabled"() {
		given:
		SecretsClientProperties secretsClientProperties = new SecretsClientProperties(null, "secrets-token-audience")

		@Subject
		SecretsService service = new SecretsService(secretsClient, secretsClientProperties)

		when:
		Map<String, Object> map = block service.getSecrets()

		then:
		0 * secretsClient._

		and:
		map == [:]
	}
	
	@Issue("https://github.com/camunda/rpa-worker/issues/120")
	void "Returns empty secrets when secrets fetching errors and do not fail"() {
		given:
		SecretsClientProperties secretsClientProperties = new SecretsClientProperties("http://secrets".toURI(), "secrets-token-audience")

		@Subject
		SecretsService service = new SecretsService(secretsClient, secretsClientProperties)
		
		and:
		secretsClient.getSecrets() >> Mono.error(new RuntimeException("BANG!"))

		when:
		Map<String, Object> map = block service.getSecrets()

		then:
		map == [:]
	}

}
