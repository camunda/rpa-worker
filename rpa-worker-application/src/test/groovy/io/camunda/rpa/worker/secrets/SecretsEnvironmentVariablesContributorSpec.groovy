package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import tools.jackson.databind.ObjectMapper

class SecretsEnvironmentVariablesContributorSpec extends Specification implements PublisherUtils {

	SecretsService secretsService = Stub()
	ObjectMapper objectMapper = new ObjectMapper()

	@Subject
	EnvironmentVariablesContributor contributor = new SecretsEnvironmentVariablesContributor(secretsService, objectMapper)
	
	void "Returns correct environment variables for secrets"() {
		given:
		secretsService.getSecrets() >> Mono.just([SECRET_VAR: 'secretValue', "Secret-Var-Two": 'secretValueTwo'])

		when:
		Map<String, String> vars = block contributor.getEnvironmentVariables(null, null)

		then:
		vars.size() == 1
		objectMapper.readValue(vars['CAMUNDA_SECRETS'], Map) == [
				SECRET_VAR: 'secretValue',
				'Secret-Var-Two': 'secretValueTwo'
		]
	}
}
