package io.camunda.rpa.worker

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jetbrains.annotations.NotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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

	static final int ZEEBE_MOCK_AUTH_PORT = 18180
	static final int ZEEBE_MOCK_SECRETS_PORT = 18181
	
	static final String ZEEBE_CLIENT_ID = "the-client-id"
	static final String ZEEBE_CLIENT_SECRET = "the-client-secret"

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

	MockWebServer zeebeAuth = new MockWebServer().tap {
		start(ZEEBE_MOCK_AUTH_PORT)
	}
	MockWebServer zeebeSecrets = new MockWebServer().tap {
		start(ZEEBE_MOCK_SECRETS_PORT)
	}
	
	void bypassSecretsAuth() {
		zeebeAuth.setDispatcher(new Dispatcher() {
			@Override
			MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
				with(new JsonSlurper().parseText(recordedRequest.getBody().readUtf8()) as Map) {
					assert client_id == AbstractFunctionalSpec.ZEEBE_CLIENT_ID
					assert client_secret == AbstractFunctionalSpec.ZEEBE_CLIENT_SECRET
					assert audience == "secrets.camunda.io"
					assert grant_type == "client_credentials"
				}
				return new MockResponse().tap {
					setResponseCode(200)
					setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					setBody(JsonOutput.toJson([
							access_token: "the-access-token",
							expires_in  : 3600
					]))
				}
			}
		})
	}
	
	void withNoSecrets() {
		bypassSecretsAuth()

		zeebeSecrets.setDispatcher(new Dispatcher() {
			@Override
			MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
				
				assert recordedRequest.getHeader(HttpHeaders.AUTHORIZATION) == "Bearer the-access-token"
				
				return new MockResponse().tap {
					setResponseCode(200)
					setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					setBody(JsonOutput.toJson([:]))
				}
			}
		})
	}

	void withSimpleSecrets(Map<String, String> secrets) {
		bypassSecretsAuth()

		zeebeSecrets.setDispatcher(new Dispatcher() {
			@Override
			MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {

				assert recordedRequest.getHeader(HttpHeaders.AUTHORIZATION) == "Bearer the-access-token"

				return new MockResponse().tap {
					setResponseCode(200)
					setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					setBody(JsonOutput.toJson(secrets))
				}
			}
		})
	}

	void cleanup() {
		zeebeAuth.close()
		zeebeSecrets.close()
	}

	protected static <T> Function<ClientResponse, Mono<ResponseEntity<T>>> toResponseEntity(Class<T> klass) {
		return (cr) -> cr.bodyToMono(klass)
				.map { str -> ResponseEntity.status(cr.statusCode()).body(str) }
	}

}
