package io.camunda.rpa.worker.secrets

import com.fasterxml.jackson.databind.ObjectMapper
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor
import io.camunda.zeebe.client.api.response.ActivatedJob
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class SecretsEnvironmentVariablesContributorSpec extends Specification implements PublisherUtils {

	SecretsService secretsService = Stub()
	ObjectMapper objectMapper = new ObjectMapper()

	@Subject
	EnvironmentVariablesContributor contributor = new SecretsEnvironmentVariablesContributor(secretsService, objectMapper)
	
	void "Returns correct environment variables for secrets"() {
		given:
		secretsService.getSecrets() >> Mono.just([SECRET_VAR: 'secretValue', "Secret-Var-Two": 'secretValueTwo'])
		ActivatedJob job = Stub()

		when:
		Map<String, String> vars = block contributor.getEnvironmentVariables(null, null)
				.contextWrite { ctx -> ctx.put(ActivatedJob, job) }

		then:
		vars.size() == 1
		objectMapper.readValue(vars['CAMUNDA_SECRETS'], Map) == [
				SECRET_VAR: 'secretValue',
				'Secret-Var-Two': 'secretValueTwo'
		]
	}
}
