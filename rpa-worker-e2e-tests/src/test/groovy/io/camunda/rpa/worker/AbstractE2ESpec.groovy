package io.camunda.rpa.worker


import com.fasterxml.jackson.databind.ObjectMapper
import feign.FeignException
import groovy.json.JsonOutput
import groovy.text.StreamingTemplateEngine
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.files.DocumentClient
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.rpa.worker.operate.OperateClient.GetProcessInstanceResponse
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.intellij.lang.annotations.Language
import org.spockframework.lang.ConditionBlock
import org.spockframework.runtime.ConditionNotSatisfiedError
import org.spockframework.runtime.GroovyRuntimeUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.mock.env.MockPropertySource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ActiveProfilesResolver
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

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

	final Retry waitForObjectRetrySpec = Retry.fixedDelay(30, Duration.ofSeconds(3))
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
	DocumentClient documentClient

	SpecificationHelper spec = new SpecificationHelper()

	@Autowired
	private WebClient.Builder webClientBuilder

	private WebClient $$webClient
	@Delegate
	WebClient getWebClient() {
		if ( ! $$webClient)
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

	DeploymentEvent deployScriptFile(String script) {
		String scriptBody = getClass().getResource("/${script}.robot").text
		return zeebeClient.newDeployResourceCommand()
				.addResourceStringUtf8(JsonOutput.toJson([
						id    : script,
						name  : script,
						script: scriptBody]), "${script}.rpa")
				.send()
				.join()
	}

	DeploymentEvent deployScript(String name, @Language("Robot") String script) {
		return zeebeClient.newDeployResourceCommand()
				.addResourceStringUtf8(JsonOutput.toJson([
						id    : name,
						name  : name,
						script: script]), "${script}.rpa")
				.send()
				.join()
	}

	DeploymentEvent deployProcess(String process) {
		return zeebeClient.newDeployResourceCommand()
				.addResourceFromClasspath("${process}.bpmn")
				.send()
				.join()
	}

	DeploymentEvent deploySimpleRobotProcess(String processId, String scriptId, String workerTag = "default") {
		getClass().getResource("/simple_rpa_process_template.bpmn").withReader { r ->
			PipedInputStream pin = new PipedInputStream()
			PipedOutputStream pout = new PipedOutputStream(pin)

			Thread.start {
				OutputStreamWriter writer = new OutputStreamWriter(pout)

				new StreamingTemplateEngine().createTemplate(r).make([
						processId  : processId,
						scriptId   : scriptId,
						workerTag  : workerTag])
						.writeTo(writer)

				writer.close()
				pout.close()
			}

			return zeebeClient.newDeployResourceCommand()
					.addResourceStream(pin, "${processId}.bpmn")
					.send()
					.join()
		}
	}

	ProcessInstanceEvent createInstance(Map<String, Object> inputVariables = [:], String process) {
		return zeebeClient.newCreateInstanceCommand()
				.bpmnProcessId(process)
				.latestVersion()
				.variables(inputVariables)
				.send()
				.join()
	}

	Mono<GetProcessInstanceResponse> getProcessInstance(long key) {
		return operateClient.getProcessInstance(key)
				.doOnSubscribe { log.info("Fetching Process Instance") }
				.doOnError { log.info("Process Instance not in Operate yet") }
				.doOnNext { log.info("Got Process Instance") }
	}

	GetProcessInstanceResponse waitForProcessInstance(long key) {
		return getProcessInstance(key)
				.retryWhen(waitForObjectRetrySpec)
				.block()
	}

	void expectNoIncident(long processInstanceKey) {
		block(getProcessInstance(processInstanceKey)
				.retryWhen(waitForObjectRetrySpec)
				.flatMap { pinstance ->
					operateClient.getIncidents(
							new OperateClient.GetIncidentsRequest(
									new OperateClient.GetIncidentsRequest.Filter(
											pinstance.key())))

							.doOnSubscribe { log.info("Fetching Incidents") }
				}
				.doOnNext { resp ->
					with(resp.items()) { incidents -> 
						incidents.empty
					}
				})
	}

	Map<String, String> getInstanceVariables(long processInstanceKey) {
		return block(operateClient.getVariables(new OperateClient.GetVariablesRequest(new OperateClient.GetVariablesRequest.Filter(processInstanceKey)))
				.flatMapIterable(it -> it.items())
				.collect(Collectors.toMap(
						(OperateClient.GetVariablesResponse.Item kv) -> kv.name(),
						(OperateClient.GetVariablesResponse.Item kv) -> 
								objectMapper.readValue(kv.value() ?: "{}", Object))))
	}

	private class SpecificationHelper {

		@ConditionBlock
		GetProcessInstanceResponse waitForProcessInstance(
				long key,
				@DelegatesTo(GetProcessInstanceResponse)
				@ClosureParams(
						value = SimpleType,
						options = "io.camunda.rpa.worker.operate.OperateClient.GetProcessInstanceResponse") Closure<?> fn) {

			return getProcessInstance(key)
					.publishOn(Schedulers.boundedElastic())
					.doOnNext { resp -> {
							Closure<?> fn2 = fn.rehydrate(resp, fn.owner, fn.thisObject).curry(resp)
							GroovyRuntimeUtil.invokeClosure(fn2)
						}
					}
					.retryWhen(waitForObjectRetrySpec)
					.block()
		}

		@ConditionBlock
		List expectIncidents(
				long processInstanceKey,
				@DelegatesTo(GetProcessInstanceResponse)
				@ClosureParams(
						value = FromString,
						options = "java.util.List<io.camunda.rpa.worker.operate.OperateClient.GetIncidentsResponse.Item>") Closure<?> fn) {

			return block(getProcessInstance(processInstanceKey)
					.flatMap { pinstance ->
						operateClient.getIncidents(
								new OperateClient.GetIncidentsRequest(
										new OperateClient.GetIncidentsRequest.Filter(
												pinstance.key())))

								.doOnSubscribe { log.info("Fetching Incidents") }
					}
					.doOnNext { resp ->
						Closure<?> fn2 = fn.rehydrate(resp, fn.owner, fn.thisObject).curry(resp.items())
						GroovyRuntimeUtil.invokeClosure(fn2)
					}
					.retryWhen(waitForObjectRetrySpec)
					.map { it.items() })
		}
	}

	static InputStream download(Flux<DataBuffer> source) {
		return DataBufferUtils.subscriberInputStream(source, 1)
	}
}
