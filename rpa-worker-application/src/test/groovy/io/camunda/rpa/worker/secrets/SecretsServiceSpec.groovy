package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.PublisherUtils
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class SecretsServiceSpec extends Specification implements PublisherUtils {
	
	void "Fetches secrets from backend when enabled"() {
		given:
		SecretsBackend backend = Mock()
		
		and:
		@Subject
		SecretsService service = new SecretsService(backend)

		when:
		Map<String, Object> map = block service.getSecrets()
		
		then:
		1 * backend.getSecrets() >> Mono.just([secretVar: 'secret-value'])
		
		and:
		map == [secretVar: 'secret-value']
	}

	void "Returns empty secrets when not enabled"() {
		given:
		@Subject
		SecretsService service = new SecretsService(null)

		when:
		Map<String, Object> map = block service.getSecrets()

		then:
		map == [:]
	}
	
	@Issue("https://github.com/camunda/rpa-worker/issues/120")
	void "Returns empty secrets when secrets fetching errors and do not fail"() {
		given:
		SecretsBackend backend = Stub()

		and:
		@Subject
		SecretsService service = new SecretsService(backend)
		
		and:
		backend.getSecrets() >> Mono.error(new RuntimeException("BANG!"))

		when:
		Map<String, Object> map = block service.getSecrets()

		then:
		map == [:]
	}
}
