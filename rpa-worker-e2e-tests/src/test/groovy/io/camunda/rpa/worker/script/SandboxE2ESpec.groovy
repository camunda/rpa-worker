package io.camunda.rpa.worker.script

import io.camunda.rpa.worker.E2EProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("local")
class SandboxE2ESpec extends Specification {
	
	@Autowired
	E2EProperties e2eProperties

	@Autowired
	private WebClient.Builder webClientBuilder
	private WebClient $$webClient
	@Delegate
	WebClient getWebClient() {
		if ( ! $$webClient)
			$$webClient = webClientBuilder.baseUrl("http://127.0.0.1:36227").build()

		return $$webClient
	}

	static Map<String, String> getEnvironment() {
		return [
				JSON_LOGGING_ENABLED: 'false',
				CAMUNDA_CLIENT_MODE: 'selfmanaged',
				CAMUNDA_CLIENT_AUTH_CLIENTID: 'zeebe',
				CAMUNDA_CLIENT_AUTH_CLIENTSECRET: '9DAMljh1he',
				CAMUNDA_CLIENT_ZEEBE_RESTADDRESS: 'http://zeebe.camunda-ciab.local',
				CAMUNDA_CLIENT_ZEEBE_GRPCADDRESS: 'http://zeebe.camunda-ciab.local',
				CAMUNDA_CLIENT_IDENTITY_BASEURL: 'http://camunda-ciab.local/auth',
				CAMUNDA_CLIENT_AUTH_ISSUER: 'http://camunda-ciab.local/auth/realms/camunda-platform/protocol/openid-connect/token',
				CAMUNDA_RPA_ZEEBE_AUTHENDPOINT: 'http://camunda-ciab.local/auth/realms/camunda-platform/protocol/openid-connect',
				CAMUNDA_CLIENT_ZEEBE_BASEURL: 'http://localhost:8080/zeebe',
				CAMUNDA_RPA_ZEEBE_SECRETS_SECRETSENDPOINT: 'http://FIXME-no-secrets/'
		]
	}
	
	static Process process
	
	void setup() {
		ProcessBuilder pb = new ProcessBuilder(e2eProperties.pathToWorker().toAbsolutePath().toString())
		pb.environment().putAll(getEnvironment())
		pb.inheritIO()
		process = pb.start()


		get().uri("/actuator/health")
				.retrieve()
				.toBodilessEntity()
				.retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
				.block()
	}
	
	void cleanup() {
		process.toHandle().destroy()
		process.waitFor(10, TimeUnit.SECONDS)
		process.toHandle().destroyForcibly()
	}
	
	void "???"() {
		expect:
		true
	}
}
