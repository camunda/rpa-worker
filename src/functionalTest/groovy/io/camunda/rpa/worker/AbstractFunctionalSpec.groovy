package io.camunda.rpa.worker

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Specification

@SpringBootTest(
		classes = [ RpaWorkerApplication, FunctionalTestConfiguration ],
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("ftest")
@ContextConfiguration(initializers = [ FunctionalTestConfiguration.StaticPropertyProvidingInitializer ])
abstract class AbstractFunctionalSpec extends Specification implements PublisherUtils {
	
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
}
