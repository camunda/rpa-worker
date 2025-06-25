package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.files.DocumentClient
import okhttp3.mockwebserver.MockResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.web.util.UriComponentsBuilder

import java.util.concurrent.TimeUnit

@TestPropertySource(properties = [
		"camunda.rpa.zeebe.auth-method=cookie", 
		"camunda.client.auth.client-id=username",
		"camunda.client.auth.client-secret=password"])
class C8RunAuthenticationIntegrationSpec extends AbstractZeebeFunctionalSpec {
	
	@Autowired
	DocumentClient documentClient
	
	@Autowired
	ResourceClient resourceClient
	
	void "Uses username/password to do cookie authentication when configured"() {
		given:
		zeebeApi.enqueue(new MockResponse().tap {
			setResponseCode(204)
			setHeader("Set-Cookie", "OPERATE-SESSION=the-auth-token; Path=/; HttpOnly; SameSite=Lax")
		})
		
		when:
		documentClient.getDocument("", "", "").subscribe()
		
		then:
		with(zeebeApi.takeRequest(2, TimeUnit.SECONDS)) {
			with(UriComponentsBuilder.fromUri(it.requestUrl.uri()).build().getQueryParams().asSingleValueMap()) {
				username == "username"
				password == "password"
			}
		}
		
		expect:
		with(zeebeApi.takeRequest(2, TimeUnit.SECONDS)) {
			getHeader(HttpHeaders.COOKIE).contains("OPERATE-SESSION=the-auth-token")
		}
		
		when:
		resourceClient.getRpaResource("").subscribe()
		
		then:
		with(zeebeApi.takeRequest(2, TimeUnit.SECONDS)) {
			getHeader(HttpHeaders.COOKIE).contains("OPERATE-SESSION=the-auth-token")
		}
	}
}
