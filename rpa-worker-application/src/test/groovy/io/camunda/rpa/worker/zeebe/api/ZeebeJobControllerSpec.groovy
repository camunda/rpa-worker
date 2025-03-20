package io.camunda.rpa.worker.zeebe.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.StubbedResponseGenerator
import io.camunda.rpa.worker.zeebe.ZeebeJobService
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ZeebeJobControllerSpec extends Specification implements PublisherUtils {
	
	ZeebeClient zeebeClient = Mock()
	ZeebeJobService zeebeJobService = Mock()
	StubbedResponseGenerator stubbedResponseGenerator = Stub()

	@Subject
	ZeebeJobController controller = new ZeebeJobController(zeebeClient, zeebeJobService, stubbedResponseGenerator)
	
	void "Sends basic throw error command to Zeebe"() {
		given:
		stubbedResponseGenerator.stubbedResponse("Zeebe", "newThrowErrorCommand", _) >> Mono.empty()
		
		when:
		block controller.throwError(123L, JobThrowErrorRequest.builder()
				.errorCode("THE_CODE")
				.build())
		
		then:
		1 * zeebeClient.newThrowErrorCommand(123L) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("THE_CODE") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * send()
			}
		}
		1 * zeebeJobService.pushDetached(123L)
	}

	void "Sends throw error command to Zeebe with message"() {
		given:
		stubbedResponseGenerator.stubbedResponse("Zeebe", "newThrowErrorCommand", _) >> Mono.empty()

		when:
		block controller.throwError(123L, JobThrowErrorRequest.builder()
				.errorCode("THE_CODE")
				.errorMessage("The message")
				.build())

		then:
		1 * zeebeClient.newThrowErrorCommand(123L) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("THE_CODE") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage("The message") >> it
				1 * send()
			}
		}
		1 * zeebeJobService.pushDetached(123L)
	}

	void "Sends throw error command to Zeebe with variables"() {
		given:
		Map<String, String> someVariables = [var1: 'val1', var2: 'val2']
		
		and:
		stubbedResponseGenerator.stubbedResponse("Zeebe", "newThrowErrorCommand", _) >> Mono.empty()

		when:
		block controller.throwError(123L, JobThrowErrorRequest.builder()
				.errorCode("THE_CODE")
				.variables(someVariables)
				.build())

		then:
		1 * zeebeClient.newThrowErrorCommand(123L) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("THE_CODE") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * variables(someVariables) >> it
				1 * send()
			}
		}
		1 * zeebeJobService.pushDetached(123L)
	}

	void "Returns stubbed response"() {
		given:
		ResponseEntity<?> stubbedResponse = Stub()
		stubbedResponseGenerator.stubbedResponse("Zeebe", "newThrowErrorCommand", _) >> Mono.just(stubbedResponse)

		when:
		ResponseEntity<?> r = block controller.throwError(123L, JobThrowErrorRequest.builder().build())

		then:
		r == stubbedResponse
	}

}
