package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.util.HttpHeaderUtils
import org.springframework.http.HttpHeaders
import reactivefeign.client.ReactiveHttpResponse
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration

class ZeebeAuthenticationServiceC8RunSpec extends Specification implements PublisherUtils {
	
	static class ZeebeAuthenticationServiceCookieAuthSpec extends Specification implements PublisherUtils {

		AuthClient authClient = Mock()
		C8RunAuthClient c8RunAuthClient = Mock()
		ZeebeProperties zeebeProperties = ZeebeProperties.builder().authMethod(ZeebeProperties.AuthMethod.COOKIE).build()

		@Subject
		ZeebeAuthenticationService service = new ZeebeAuthenticationService(authClient, c8RunAuthClient, zeebeProperties, Duration.ofSeconds(2))

		void "Uses cached authentication token"() {
			when:
			String token1 = block service.getAuthToken("username", "password", null)

			then:
			1 * c8RunAuthClient.login("username", "password") >> Mono.just(Stub(ReactiveHttpResponse) {
				headers() >> [(HttpHeaders.SET_COOKIE): ["OPERATE-SESSION=the-access-token; Path=/; HttpOnly; SameSite=Lax"]]
			})

			and:
			token1 == "the-access-token"

			when:
			String token2 = block service.getAuthToken("username", "password", null)

			then:
			0 * c8RunAuthClient.login(_, _)

			and:
			token2 == "the-access-token"
		}

		void "Refreshes cached auth token when expired"() {
			when:
			String token1 = block service.getAuthToken("username", "password", null)

			then:
			1 * c8RunAuthClient.login("username", "password") >> Mono.just(Stub(ReactiveHttpResponse) {
				headers() >> [(HttpHeaders.SET_COOKIE): ["OPERATE-SESSION=first-access-token; Path=/; HttpOnly; SameSite=Lax"]]
			})

			and:
			token1 == "first-access-token"

			when:
			String token2 = block Mono.delay(Duration.ofMillis(2_500))
					.then(service.getAuthToken("username", "password", null))

			then:
			1 * c8RunAuthClient.login("username", "password") >> Mono.just(Stub(ReactiveHttpResponse) {
				headers() >> [(HttpHeaders.SET_COOKIE): ["OPERATE-SESSION=second-access-token; Path=/; HttpOnly; SameSite=Lax"]]
			})

			and:
			token2 == "second-access-token"
		}
	}

	static class ZeebeAuthenticationServiceBasicAuthSpec extends Specification implements PublisherUtils {

		AuthClient authClient = Mock()
		C8RunAuthClient c8RunAuthClient = Mock()
		ZeebeProperties zeebeProperties = ZeebeProperties.builder().authMethod(ZeebeProperties.AuthMethod.BASIC).build()

		@Subject
		ZeebeAuthenticationService service = new ZeebeAuthenticationService(authClient, c8RunAuthClient, zeebeProperties, Duration.ofSeconds(2))

		void "Returns basic auth header for requests"() {
			when:
			String token1 = block service.getAuthToken("username", "password", null)

			then:
			token1 == HttpHeaderUtils.basicAuth("username", "password")
		}
	}

	static class ZeebeAuthenticationServiceNoAuthSpec extends Specification implements PublisherUtils {

		AuthClient authClient = Mock()
		C8RunAuthClient c8RunAuthClient = Mock()
		ZeebeProperties zeebeProperties = ZeebeProperties.builder().authMethod(ZeebeProperties.AuthMethod.NONE).build()

		@Subject
		ZeebeAuthenticationService service = new ZeebeAuthenticationService(authClient, c8RunAuthClient, zeebeProperties, Duration.ofSeconds(2))

		void "Returns empty authenticator"() {
			when:
			String token1 = block service.getAuthToken("username", "password", null)

			then:
			! token1
		}
	}
}
