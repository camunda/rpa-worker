package io.camunda.rpa.worker.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.zeebe.ZeebeClientStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject

class StubbedResponseGeneratorSpec extends Specification implements PublisherUtils {
	
	ZeebeClientStatus zeebeClientStatus = Stub()
	
	@Subject
	StubbedResponseGenerator stubbedResponseGenerator = new StubbedResponseGenerator(zeebeClientStatus)
	
	Object aRequest = Stub()
	
	void "Returns correct stubbed response when no Zeebe"() {
		given:
		zeebeClientStatus.isZeebeClientEnabled() >> false
		
		when:
		ResponseEntity<?> r = block stubbedResponseGenerator.stubbedResponse("target", "action", aRequest)
		
		then:
		r.statusCode == HttpStatus.NOT_IMPLEMENTED
		with(r.getBody() as StubbedResponse) {
			target() == "target"
			action() == "action"
			request() == aRequest
		}
	}

	void "Returns empty when Zeebe is enabled"() {
		given:
		zeebeClientStatus.isZeebeClientEnabled() >> true

		when:
		ResponseEntity<?> r = block stubbedResponseGenerator.stubbedResponse("target", "action", aRequest)

		then:
		! r
	}
}
