package io.camunda.rpa.worker

import io.camunda.zeebe.client.ZeebeClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.env.MockPropertySource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ActiveProfilesResolver
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles(resolver = ProfileResolver)
@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
class AbstractE2ESpec extends Specification {

	static class ProfileResolver implements ActiveProfilesResolver {
		@Override
		String[] resolve(Class<?> testClass) {
			System.getenv("CI") && System.getenv("E2E")
					? ([] as String[])
					: (["local"] as String[])
		}
	}

	@Autowired
	E2EProperties e2eProperties
	
	@Autowired
	ZeebeClient zeebeClient
	
	@Autowired
	private WebClient.Builder webClientBuilder
	
	private WebClient $$webClient
	@Delegate
	WebClient getWebClient() {
		if (!$$webClient)
			$$webClient = webClientBuilder.baseUrl("http://127.0.0.1:36227").build()

		return $$webClient
	}

	Map<String, String> getEnvironment() {
		String camundaHost = e2eProperties.camundaHost() ?: "camunda.local"
		String clientSecret = e2eProperties.clientSecret() ?: System.getenv("CAMUNDA_CLIENT_AUTH_CLIENTSECRET")

		return [
				JSON_LOGGING_ENABLED                     : 'false',
				CAMUNDA_CLIENT_MODE                      : 'selfmanaged',
				CAMUNDA_CLIENT_AUTH_CLIENTID             : 'zeebe',
				CAMUNDA_CLIENT_AUTH_CLIENTSECRET         : clientSecret,
				CAMUNDA_CLIENT_ZEEBE_RESTADDRESS         : "http://zeebe.${camundaHost}",
				CAMUNDA_CLIENT_ZEEBE_GRPCADDRESS         : "http://zeebe.${camundaHost}",
				CAMUNDA_CLIENT_IDENTITY_BASEURL          : "http://${camundaHost}/auth",
				CAMUNDA_CLIENT_AUTH_ISSUER               : "http://${camundaHost}/auth/realms/camunda-platform/protocol/openid-connect/token",
				CAMUNDA_RPA_ZEEBE_AUTHENDPOINT           : "http://${camundaHost}/auth/realms/camunda-platform/protocol/openid-connect",
				CAMUNDA_CLIENT_ZEEBE_BASEURL             : 'http://localhost:8080/zeebe',
				CAMUNDA_RPA_ZEEBE_SECRETS_SECRETSENDPOINT: 'http://FIXME-no-secrets/'
		].collectEntries { k, v -> [k, v.toString()] }
	}

	static class StaticPropertyProvidingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		@Override
		void initialize(ConfigurableApplicationContext applicationContext) {
			
			String camundaHost = System.getenv("CAMUNDA_RPA_E2E_CAMUNDAHOST") ?: "camunda.local"
			String clientSecret = System.getenv("CAMUNDA_RPA_E2E_CLIENTSECRET") 
					?: System.getenv("CAMUNDA_CLIENT_AUTH_CLIENTSECRET")
					?: "9DAMljh1he"
			
			applicationContext.getEnvironment().propertySources.addFirst(new MockPropertySource()
					.withProperty("camunda.client.mode", "selfmanaged")
					.withProperty("camunda.client.auth.client-id", "zeebe")
					.withProperty("camunda.client.auth.client-secret", clientSecret)
					.withProperty("camunda.client.zeebe.rest-address", "http://zeebe.${camundaHost}")
					.withProperty("camunda.client.zeebe.grpc-address", "http://zeebe.${camundaHost}")
					.withProperty("camunda.client.identity.base-url", "http://${camundaHost}/auth/")
					.withProperty("camunda.client.auth.issuer", "http://${camundaHost}/auth/realms/camunda-platform/protocol/openid-connect/token")
					.withProperty("camunda.rpa.zeebe.auth-endpoint", "http://${camundaHost}/auth/realms/camunda-platform/protocol/openid-connect")
					.withProperty("camunda.client.zeebe.base-url", 'http://localhost:8080/zeebe')
					.withProperty("camunda.client.zeebe.audience", "zeebe.${camundaHost}")
					.withProperty("camunda.rpa.zeebe.secrets.secrets-endpoint", 'http://FIXME-no-secrets'))
		}
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
				.retryWhen(Retry.fixedDelay(100, Duration.ofSeconds(3)))
				.block()
	}

	void cleanup() {
		process.toHandle().destroy()
		process.waitFor(10, TimeUnit.SECONDS)
		process.toHandle().destroyForcibly()
	}
}
