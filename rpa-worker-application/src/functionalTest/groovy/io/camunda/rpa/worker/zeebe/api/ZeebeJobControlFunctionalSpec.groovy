package io.camunda.rpa.worker.zeebe.api

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.ThrowErrorCommandStep1
import io.camunda.client.api.response.ActivatedJob
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.api.CallbackTokenService
import io.camunda.rpa.worker.workspace.Workspace
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.spockframework.spring.SpringBean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.test.context.TestPropertySource

import java.nio.file.Path

import static io.camunda.rpa.worker.zeebe.ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY

@TestPropertySource(properties = "camunda.rpa.sandbox.enabled=false")
class ZeebeJobControlFunctionalSpec extends AbstractFunctionalSpec {
	
	@SpringBean
	CamundaClient camundaClient = Mock()

	@Autowired
	CallbackTokenService callbackTokens
	
	void "Issues throw error command to Zeebe and returns correct status to client"() {
		given:
		Map<String, String> someVariables = [var1: 'val1', var2: 'val2']
		ActivatedJob job = Stub() { getKey() >> 123456L }
		Workspace workspace = new Workspace("production", Path.of("/production"), [(ZEEBE_JOB_WORKSPACE_PROPERTY): job], null)
		callbackTokens.openExecution(workspace)
		String token = block(callbackTokens.getEnvironmentVariables(workspace, null)).RPA_CALLBACK_TOKEN

		when:
		ResponseEntity<Void> resp = block post()
				.uri("/zeebe/job/123456/throw")
				.headers { it.setBearerAuth(token) }
				.body(BodyInserters.fromValue(JobThrowErrorRequest.builder()
						.errorCode("ERROR_CODE")
						.errorMessage("The error message")
						.variables(someVariables)
						.build()))
				.retrieve()
				.toBodilessEntity()
		
		then:
		1 * camundaClient.newThrowErrorCommand(123456L) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("ERROR_CODE") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage("The error message") >> it
				1 * variables(someVariables) >> it
				1 * send()
			}
		}
		
		and:
		resp.statusCode == HttpStatus.ACCEPTED

		cleanup:
		callbackTokens.afterRobotExecution(workspace)
	}

	void "A job key or administrative credential alone cannot throw an error with the sandbox disabled"() {
		given:
		def client = anonymous
				? getWebClient().mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
				: getWebClient()

		when:
		def response = block client.post()
				.uri("/zeebe/job/123456/throw")
				.bodyValue([errorCode: "INJECTED", variables: [injected: true]])
				.exchangeToMono { it.toBodilessEntity() }

		then:
		response.statusCode == HttpStatus.UNAUTHORIZED
		0 * camundaClient._

		where:
		anonymous << [true, false]
	}

	void "Revoked and other-job credentials cannot throw errors"() {
		given:
		ActivatedJob job = Stub() { getKey() >> 123456L }
		Workspace workspace = new Workspace("production", Path.of("/production"), [(ZEEBE_JOB_WORKSPACE_PROPERTY): job], null)
		callbackTokens.openExecution(workspace)
		String token = block(callbackTokens.getEnvironmentVariables(workspace, null)).RPA_CALLBACK_TOKEN
		if (revoked)
			callbackTokens.afterRobotExecution(workspace)

		when:
		def response = block post()
				.uri("/zeebe/job/$jobKey/throw")
				.headers { it.setBearerAuth(token) }
				.bodyValue([errorCode: "INJECTED"])
				.exchangeToMono { it.toBodilessEntity() }

		then:
		response.statusCode == HttpStatus.UNAUTHORIZED
		0 * camundaClient._

		cleanup:
		callbackTokens.afterRobotExecution(workspace)

		where:
		jobKey  | revoked
		123457L | false
		123456L | true
	}
}
