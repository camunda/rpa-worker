package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.files.DocumentClient
import io.camunda.rpa.worker.util.HttpHeaderUtils
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import okhttp3.mockwebserver.MockResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.web.util.UriComponentsBuilder

import java.util.concurrent.TimeUnit

class C8RunAuthenticationIntegrationSpec extends AbstractZeebeFunctionalSpec {

	@TestPropertySource(properties = [
			"camunda.rpa.zeebe.auth-method=cookie",
			"camunda.client.auth.client-id=username",
			"camunda.client.auth.client-secret=password"])
	static class C8RunCookieAuthenticationIntegrationSpec extends AbstractZeebeFunctionalSpec {

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

	@TestPropertySource(properties = [
			"camunda.rpa.zeebe.auth-method=basic",
			"camunda.client.auth.client-id=username",
			"camunda.client.auth.client-secret=password"])
	static class C8RunBasicAuthenticationIntegrationSpec extends AbstractZeebeFunctionalSpec {

		@Autowired
		DocumentClient documentClient

		@Autowired
		ResourceClient resourceClient
		
		@Autowired
		ClientInterceptor grpcClientInterceptor

		void "Uses username/password to do basic authentication when configured"() {
			given:
			2.times {
				zeebeApi.enqueue(new MockResponse().tap {
					setResponseCode(204)
				})
			}
			
			when:
			documentClient.getDocument("", "", "").subscribe()

			then:
			with(zeebeApi.takeRequest(2, TimeUnit.SECONDS)) {
				getHeader(HttpHeaders.AUTHORIZATION).contains(HttpHeaderUtils.basicAuth("username", "password"))
			}

			when:
			resourceClient.getRpaResource("").subscribe()

			then:
			with(zeebeApi.takeRequest(2, TimeUnit.SECONDS)) {
				getHeader(HttpHeaders.AUTHORIZATION).contains(HttpHeaderUtils.basicAuth("username", "password"))
			}
		}
		
		void "Basic auth is sent by grpc client"() {
			given:
			ClientCall clientCall = Stub()
			Channel channel = Stub() {
				newCall(_, _) >> { clientCall }
			}
			Metadata metadata = new Metadata()

			when:
			grpcClientInterceptor.interceptCall(null, null, channel).start(null, metadata)
			
			then:
			metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)) == HttpHeaderUtils.basicAuth("username", "password")	
		}
	}

	@TestPropertySource(properties = "camunda.rpa.zeebe.auth-method=none")
	static class C8RunNoAuthenticationIntegrationSpec extends AbstractZeebeFunctionalSpec {

		@Autowired
		DocumentClient documentClient

		@Autowired
		ResourceClient resourceClient

		void "Uses no authentication"() {
			given:
			2.times {
				zeebeApi.enqueue(new MockResponse().tap {
					setResponseCode(204)
				})
			}

			when:
			documentClient.getDocument("", "", "").subscribe()

			then:
			zeebeApi.takeRequest(2, TimeUnit.SECONDS)

			when:
			resourceClient.getRpaResource("").subscribe()

			then:
			zeebeApi.takeRequest(2, TimeUnit.SECONDS)
		}
	}
}