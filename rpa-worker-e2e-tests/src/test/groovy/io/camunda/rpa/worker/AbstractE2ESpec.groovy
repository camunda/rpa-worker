package io.camunda.rpa.worker

import com.fasterxml.jackson.databind.ObjectMapper
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
class AbstractE2ESpec extends Specification implements PublisherUtils {

	static class ProfileResolver implements ActiveProfilesResolver {
		@Override
		String[] resolve(Class<?> testClass) {
			System.getenv("CI") && System.getenv("E2E")
					? ([] as String[])
					: (["local"] as String[])
		}
	}
	
	static final ZeebeConfiguration zeebeConfiguration = ZeebeConfiguration.get()

	@Autowired
	E2EProperties e2eProperties
	
	@Autowired
	ZeebeClient zeebeClient
	
	@Autowired
	RpaWorkerClient rpaWorkerClient
	
	@Autowired
	ObjectMapper objectMapper
	
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
		return (zeebeConfiguration.environment + getExtraEnvironment())
				.collectEntries { k, v -> [k, v.toString()] }
	}
	
	protected Map<String, String> getExtraEnvironment() {
		return [:]
	}

	static class StaticPropertyProvidingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		@Override
		void initialize(ConfigurableApplicationContext applicationContext) {
			applicationContext.getEnvironment().propertySources.addFirst(
					zeebeConfiguration.installProperties(new MockPropertySource()))
		}
	}

	static Process process

	void setup() {
		ProcessBuilder pb = new ProcessBuilder(e2eProperties.pathToWorker().toAbsolutePath().toString())
		pb.environment().putAll(getEnvironment())
		pb.inheritIO()
		
		if( ! zeebeConfiguration.configProperties['camunda.rpa.e2e.no-start-worker'])
			process = pb.start()

		get().uri("/actuator/health")
				.retrieve()
				.toBodilessEntity()
				.retryWhen(Retry.fixedDelay(100, Duration.ofSeconds(3)))
				.block()
	}

	void cleanup() {
		if( ! process) return
		process.toHandle().destroy()
		process.waitFor(10, TimeUnit.SECONDS)
		process.toHandle().destroyForcibly()
	}
	
	String taskForTag(String tag) {
		return "camunda::RPA-Task::${tag}"
	}
}
