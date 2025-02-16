package io.camunda.rpa.worker.zeebe

import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Mono
import reactor.util.retry.Retry

import java.time.Duration

@Slf4j
class ZeebeE2ESpec extends AbstractE2ESpec {

	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [CAMUNDA_RPA_SCRIPTS_SOURCE: 'local']
	}
	
	@Autowired
	OperateClient operateClient

	void "Process errors with correct message when no linked resource providing main script"() {
		when:
		DeploymentEvent deploy = zeebeClient.newDeployResourceCommand()
				.addResourceFromClasspath("no_script_on_default.bpmn")
				.send()
				.join()

		and:
		ProcessInstanceEvent pinstance = zeebeClient.newCreateInstanceCommand()
				.bpmnProcessId("no_script_on_default")
				.latestVersion()
				.send()
				.join()

		then:
		block Mono.defer {
			operateClient.getProcessInstance(pinstance.processInstanceKey)
					.doOnSubscribe { log.info("Fetching Process Instance") }
					.doOnError { log.info("Process Instance not in Operate yet") }
					.doOnNext { log.info("Got Process Instance") }
		}.doOnNext { resp ->
			log.info("Checking for Incident")
			with(resp) {
				incident()
			}
			log.info("Incident is raised")
		}.doOnError { 
			log.info("Incident not raised yet") 
		}.retryWhen(Retry.backoff(4, Duration.ofSeconds(4)))

		when:
		OperateClient.GetIncidentsResponse incidents = block operateClient.getIncidents(
				new OperateClient.GetIncidentsRequest(
						new OperateClient.GetIncidentsRequest.Filter(
								pinstance.processInstanceKey)))
				.doOnSubscribe { log.info("Fetching Incidents") }
			
		then:
		incidents.items().size() == 1
		with(incidents.items().first()) {
			type() == OperateClient.GetIncidentsResponse.Item.Type.JOB_NO_RETRIES
			message() == "Failed to find exactly 1 LinkedResource providing the main script"
		}
	}
}
	
