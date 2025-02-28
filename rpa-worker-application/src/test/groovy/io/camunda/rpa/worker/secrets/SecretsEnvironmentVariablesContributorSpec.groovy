package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor
import io.camunda.zeebe.client.api.response.ActivatedJob
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class SecretsEnvironmentVariablesContributorSpec extends Specification implements PublisherUtils {

	SecretsService secretsService = Stub()

	@Subject
	EnvironmentVariablesContributor contributor = new SecretsEnvironmentVariablesContributor(secretsService)
	
	void "Returns empty variables when no Zeebe job in context"() {
		expect:
		block(contributor.getEnvironmentVariables(null, null)).isEmpty()
	}

	void "Returns correct environment variables for secrets"() {
		given:
		secretsService.getSecrets() >> Mono.just([SECRET_VAR: 'secretValue'])
		ActivatedJob job = Stub()

		when:
		Map<String, String> vars = block contributor.getEnvironmentVariables(null, null)
				.contextWrite { ctx -> ctx.put(ActivatedJob, job) }

		then:
		vars == [SECRET_SECRET_VAR: 'secretValue']
	}
}
