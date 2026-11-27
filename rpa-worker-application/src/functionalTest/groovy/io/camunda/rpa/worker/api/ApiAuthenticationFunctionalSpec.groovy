package io.camunda.rpa.worker.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["management.endpoint.health.probes.enabled=true"])
class ApiAuthenticationFunctionalSpec extends AbstractFunctionalSpec {

	void "Kubernetes health probes work without custom Host headers or credentials"() {
		when:
		def response = block getWebClient().mutate()
				.defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }
				.build().get()
				.uri(path)
				.header(HttpHeaders.HOST, "10.42.0.12:$serverPort")
				.exchangeToMono { it.toEntity(Map) }

		then:
		response.statusCode in [HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE]
		response.body.status in ["UP", "DOWN", "OUT_OF_SERVICE"]

		where:
		path << ["/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"]
	}

	void "Rejects evaluation without valid authentication before validating or executing the script"() {
		when:
		def response = block getWebClient().mutate()
				.defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }
				.build().post()
				.uri("/script/evaluate")
				.headers {
					if (token != null)
						it.setBearerAuth(token)
				}
				.bodyValue([script: "*** Tasks ***\nTask\n    Fail    Must not execute"])
				.exchangeToMono { it.toBodilessEntity() }

		then:
		response.statusCode == HttpStatus.UNAUTHORIZED

		where:
		token << [null, "", "wrong"]
	}

	void "Rejects a rebinding-style Host even with valid authentication"() {
		when:
		def response = block get()
				.uri("/api/authentication")
				.header(HttpHeaders.HOST, "rebind.example:$serverPort")
				.exchangeToMono { it.toBodilessEntity() }

		then:
		response.statusCode == HttpStatus.FORBIDDEN
	}

	void "Authenticates Modeler requests on the real HTTP listener"() {
		expect:
		block(get().uri("/api/authentication").retrieve().toBodilessEntity()).statusCode == HttpStatus.NO_CONTENT
	}
}
