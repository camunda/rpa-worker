package io.camunda.rpa.worker.util

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.net.WebClientProvisioner
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class InternetConnectivityProviderSpec extends Specification implements PublisherUtils {
	
	WebClient webClient = Mock()
	
	WebClientProvisioner webClientProvisioner = Mock() {
		webClient(_, _) >> webClient
	}
	
	@Subject
	InternetConnectivityProvider provider = new InternetConnectivityProvider(webClientProvisioner)
	
	void "Returns true when connectivity check passes"() {
		when:
		boolean r = block provider.hasConnectivity()
		
		then:
		1 * webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			1 * uri(InternetConnectivityProvider.TEST_URL) >> Mock(WebClient.RequestHeadersSpec) {
				1 * retrieve() >> Mock(WebClient.ResponseSpec) {
					1 * toBodilessEntity() >> Mono.just(ResponseEntity.ok().build())
				}
			}
		}
		
		and:
		r
	}

	void "Returns false when connectivity check fails (web client)"() {
		when:
		boolean r = block provider.hasConnectivity()

		then:
		1 * webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			1 * uri(InternetConnectivityProvider.TEST_URL) >> Mock(WebClient.RequestHeadersSpec) {
				1 * retrieve() >> Mock(WebClient.ResponseSpec) {
					1 * toBodilessEntity() >> Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build())
				}
			}
		}

		and:
		! r
	}

	void "Returns false when connectivity check fails (HTTP client)"() {
		when:
		boolean r = block provider.hasConnectivity()

		then:
		1 * webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			1 * uri(InternetConnectivityProvider.TEST_URL) >> Mock(WebClient.RequestHeadersSpec) {
				1 * retrieve() >> Mock(WebClient.ResponseSpec) {
					1 * toBodilessEntity() >> Mono.error(new SocketTimeoutException())
				}
			}
		}

		and:
		! r
	}
}
