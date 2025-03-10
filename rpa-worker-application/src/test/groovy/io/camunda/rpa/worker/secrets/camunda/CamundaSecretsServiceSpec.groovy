package io.camunda.rpa.worker.secrets.camunda

import io.camunda.rpa.worker.PublisherUtils
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class CamundaSecretsServiceSpec extends Specification implements PublisherUtils {
	
	CamundaSecretsClient camundaSecretsClient = Mock()

	@Subject
	CamundaSecretsBackend service = new CamundaSecretsBackend(this.camundaSecretsClient)

	void "Fetches and returns Camunda secrets with client"() {
		when:
		Map<String, Object> map = block service.getSecrets()
		
		then:
		1 * camundaSecretsClient.getSecrets() >> Mono.just([secretVar: 'secret-value'])
		
		and:
		map == [secretVar: 'secret-value']
	}
}
