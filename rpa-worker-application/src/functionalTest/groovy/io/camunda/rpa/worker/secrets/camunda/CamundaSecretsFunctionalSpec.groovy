package io.camunda.rpa.worker.secrets.camunda

import groovy.json.JsonOutput
import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.secrets.SecretsService
import okhttp3.mockwebserver.MockResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext

import java.util.concurrent.TimeUnit

@DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
class CamundaSecretsFunctionalSpec extends AbstractFunctionalSpec {
	
	@Autowired
	SecretsService secretsService
	
	@DirtiesContext
	void "Authenticates and fetches secrets"() {
		given:
		zeebeAuth.enqueue(new MockResponse().tap {
			setResponseCode(200)
			setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			setBody(JsonOutput.toJson([
					access_token: "the-access-token",
					expires_in  : 3600
			]))
		})
		
		and:
		zeebeSecrets.enqueue(new MockResponse().tap {
			setResponseCode(200)
			setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			setBody(JsonOutput.toJson([secretVar: 'secret-value']))
		})
		
		when:
		Map<String, String> secrets = block secretsService.getSecrets()
		
		then:
		with(zeebeAuth.takeRequest(2, TimeUnit.SECONDS)) { req ->
			with(decodeForm(req.getBody().readUtf8()) as Map) {
				client_id == AbstractFunctionalSpec.ZEEBE_CLIENT_ID
				client_secret == AbstractFunctionalSpec.ZEEBE_CLIENT_SECRET
				audience == "secrets.camunda.io"
				grant_type == "client_credentials"
			}
		}
		
		and:
		with(zeebeSecrets.takeRequest(2, TimeUnit.SECONDS)) { req ->
			req.getHeader(HttpHeaders.AUTHORIZATION) == "Bearer the-access-token"
		}

		and:
		secrets == [secretVar: 'secret-value']
	}
	
	@DirtiesContext
	void "Uses cached authentication token"() {
		given:
		zeebeAuth.enqueue(new MockResponse().tap {
			setResponseCode(200)
			setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			setBody(JsonOutput.toJson([
					access_token: "the-access-token",
					expires_in  : 3600
			]))
		})

		and:
		2.times {
			zeebeSecrets.enqueue(new MockResponse().tap {
				setResponseCode(200)
				setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				setBody(JsonOutput.toJson([secretVar: 'secret-value']))
			})
		}

		when:
		block secretsService.getSecrets()

		then:
		zeebeAuth.takeRequest(2, TimeUnit.SECONDS)

		when:
		block secretsService.getSecrets()

		then:
		! zeebeAuth.takeRequest(2, TimeUnit.SECONDS)
	}
	
	@DirtiesContext
	void "Refreshes cached auth token when expired"() {
		given:
		2.times {
			zeebeAuth.enqueue(new MockResponse().tap {
				setResponseCode(200)
				setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				setBody(JsonOutput.toJson([
						access_token: "the-access-token",
						expires_in  : 0
				]))
			})
		}

		and:
		2.times {
			zeebeSecrets.enqueue(new MockResponse().tap {
				setResponseCode(200)
				setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				setBody(JsonOutput.toJson([secretVar: 'secret-value']))
			})
		}

		when:
		block secretsService.getSecrets()

		then:
		zeebeAuth.takeRequest(2, TimeUnit.SECONDS)

		when:
		block secretsService.getSecrets()

		then:
		zeebeAuth.takeRequest(2, TimeUnit.SECONDS)
	}
}
