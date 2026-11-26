package io.camunda.rpa.worker.zeebe.api

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ThrowErrorCommandStep1
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.StubbedResponseGenerator
import io.camunda.rpa.worker.zeebe.ZeebeJobService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ZeebeJobControllerSpec extends Specification implements PublisherUtils {
	
	CamundaClient zeebeClient = Mock()
	ZeebeJobService zeebeJobService = Mock()
	ObjectProvider<CamundaClient> zeebeClientsProvider = Stub() {
		getObject() >> zeebeClient
	}
	ObjectProvider<ZeebeJobService> zeebeJobServiceProvider = Stub() {
		getObject() >> zeebeJobService
	}
	StubbedResponseGenerator stubbedResponseGenerator = Stub()

	@Subject
	ZeebeJobController controller = new ZeebeJobController(zeebeClientsProvider, zeebeJobServiceProvider, stubbedResponseGenerator)
	
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

	void "Sandbox marker cannot issue a real cluster command"() {
		given:
		def responseStub = ResponseEntity.status(501).body("stub")
		stubbedResponseGenerator.createStubbedResponse("Zeebe", "newThrowErrorCommand", _) >> Mono.just(responseStub)

		when:
		def response = block controller.throwError(-1L, JobThrowErrorRequest.builder().errorCode("ERROR").build())

		then:
		response == responseStub
		0 * zeebeClient._
		0 * zeebeJobService._
	}

	void "Other nonpositive job keys cannot issue a real cluster command"() {
		given:
		stubbedResponseGenerator.stubbedResponse("Zeebe", "newThrowErrorCommand", _) >> Mono.empty()

		when:
		def response = block controller.throwError(jobKey, JobThrowErrorRequest.builder().errorCode("ERROR").build())

		then:
		response.statusCode.value() == 400
		0 * zeebeClient._
		0 * zeebeJobService._

		where:
		jobKey << [0L, -2L]
	}

}
