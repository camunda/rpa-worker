package io.camunda.rpa.worker.zeebe.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1
import org.spockframework.spring.SpringBean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.BodyInserters

class ZeebeJobControlFunctionalSpec extends AbstractFunctionalSpec {
	
	@SpringBean
	ZeebeClient zeebeClient = Mock()
	
	void "Issues throw error command to Zeebe and returns correct status to client"() {
		given:
		Map<String, String> someVariables = [var1: 'val1', var2: 'val2']

		when:
		ResponseEntity<Void> resp = block post()
				.uri("/zeebe/job/123456/throw")
				.body(BodyInserters.fromValue(JobThrowErrorRequest.builder()
						.errorCode("ERROR_CODE")
						.errorMessage("The error message")
						.variables(someVariables)
						.build()))
				.retrieve()
				.toBodilessEntity()
		
		then:
		1 * zeebeClient.newThrowErrorCommand(123456L) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ERROR_CODE") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage("The error message") >> it
				1 * variables(someVariables) >> it
				1 * send()
			}
		}
		
		and:
		resp.statusCode == HttpStatus.ACCEPTED
	}
}
