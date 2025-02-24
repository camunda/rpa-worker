package io.camunda.rpa.worker

import com.fasterxml.jackson.databind.ObjectMapper
import feign.FeignException
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.rpa.worker.zeebe.ResourceClient
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.spockframework.runtime.ConditionNotSatisfiedError
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.env.MockPropertySource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ActiveProfilesResolver
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Function

@SpringBootTest
@ActiveProfiles(resolver = ProfileResolver)
@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
@Slf4j
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

	final Retry waitForObjectRetrySpec = Retry.fixedDelay(15, Duration.ofSeconds(2))
			.filter { thrown ->
				thrown instanceof ConditionNotSatisfiedError
						|| thrown instanceof FeignException.NotFound
			}

	@Autowired
	E2EProperties e2eProperties
	
	@Autowired
	ZeebeClient zeebeClient
	
	@Autowired
	RpaWorkerClient rpaWorkerClient
	
	@Autowired
	ObjectMapper objectMapper
	
	@Autowired
	OperateClient operateClient
	
	@Autowired
	ResourceClient resourceClient

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

		if ( ! zeebeConfiguration.configProperties['camunda.rpa.e2e.no-start-worker'])
			process = pb.start()

		get().uri("/actuator/health")
				.retrieve()
				.toBodilessEntity()
				.retryWhen(Retry.fixedDelay(100, Duration.ofSeconds(3))
						.filter { ( ! process) || process.alive })
				.onErrorMap(thrown -> new IllegalStateException("RPA Worker did not become available", thrown))
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

	DeploymentEvent deployScript(String script) {
		String scriptBody = getClass().getResource("/${script}.robot").text
		DeploymentEvent deployment = zeebeClient.newDeployResourceCommand()
				.addResourceStringUtf8(JsonOutput.toJson([
						id    : script,
						name  : script,
						script: scriptBody]), "${script}.rpa")
				.send()
				.join()
		log.info("Deployed script, got {}", deployment)
		
		resourceClient.getRpaResource(deployment.key.toString())
			.retryWhen(waitForObjectRetrySpec)
			.doOnError { log.error("Deployed script did not show up") }
			.onErrorComplete()
		
		return deployment
	}

	DeploymentEvent deployProcess(String process) {
		return zeebeClient.newDeployResourceCommand()
				.addResourceFromClasspath("${process}.bpmn")
				.send()
				.join()
	}

	ProcessInstanceEvent createInstance(String process, Map<String, Object> inputVariables = [:]) {
		return zeebeClient.newCreateInstanceCommand()
				.bpmnProcessId(process)
				.latestVersion()
				.variables(inputVariables)
				.send()
				.join()
	}

	Mono<OperateClient.GetProcessInstanceResponse> getProcessInstance(long key) {
		return operateClient.getProcessInstance(key)
				.doOnSubscribe { log.info("Fetching Process Instance") }
				.doOnError { log.info("Process Instance not in Operate yet") }
				.doOnNext { log.info("Got Process Instance") }
	}

	Function<OperateClient.GetProcessInstanceResponse, Mono<OperateClient.GetProcessInstanceResponse>> expectNoIncident() {
		return { pinstance ->
			if ( ! pinstance.incident())
				return Mono.just(pinstance)

			return operateClient.getIncidents(new OperateClient.GetIncidentsRequest(new OperateClient.GetIncidentsRequest.Filter(pinstance.key())))
					.map {
						with(it.items()) { incidents ->
							incidents.empty
						}
					}
		}
	}
}
