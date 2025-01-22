package io.camunda.rpa.worker

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.blockhound.BlockHound
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.function.Function

@SpringBootTest(
		classes = [ RpaWorkerApplication, FunctionalTestConfiguration ],
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ftest")
@ContextConfiguration(initializers = [ FunctionalTestConfiguration.StaticPropertyProvidingInitializer ])
abstract class AbstractFunctionalSpec extends Specification implements PublisherUtils {

	static {
		BlockHound.builder()
				.allowBlockingCallsInside(ResourceBundle.class.name, "getBundle")
				.install()
	}
	
	@Autowired
	private WebClient.Builder webClientBuilder

	@LocalServerPort
	int serverPort
	
	private WebClient $$webClient
	@Delegate
	WebClient getWebClient() {
		if( ! $$webClient) 
			$$webClient = webClientBuilder.baseUrl("http://localhost:${serverPort}").build()
		
		return $$webClient
	}

	protected static <T> Function<ClientResponse, Mono<ResponseEntity<T>>> toResponseEntity(Class<T> klass) {
		return (cr) -> cr.bodyToMono(klass)
				.map { str -> ResponseEntity.status(cr.statusCode()).body(str) }
	}

}
