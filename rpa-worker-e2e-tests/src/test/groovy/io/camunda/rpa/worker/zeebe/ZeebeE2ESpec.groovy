package io.camunda.rpa.worker.zeebe

import groovy.util.logging.Slf4j
import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.operate.OperateClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.spockframework.runtime.ConditionNotSatisfiedError

@Slf4j
class ZeebeE2ESpec extends AbstractE2ESpec {

	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [CAMUNDA_RPA_SCRIPTS_SOURCE: "zeebe"]
	}

	void "Process errors with correct message when no linked resource providing main script"() {
		when:
		deployProcess("no_script_on_default")

		and:
		ProcessInstanceEvent pinstance = createInstance("no_script_on_default")

		then:
		block getProcessInstance(pinstance.processInstanceKey)
				.doOnNext { resp ->
					log.info("Checking for Incident")
					with(resp) {
						incident()
					}
					log.info("Incident is raised")
				}
				.doOnError(ConditionNotSatisfiedError) {
					log.info("Incident not raised yet")
				}
				.retryWhen(waitForObjectRetrySpec)

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

	void "Runs deployed script, and reports success"() {
		when:
		deployScript("script_1")

		and:
		deployProcess("script_1_on_default")

		and:
		ProcessInstanceEvent pinstance = createInstance("script_1_on_default")

		then:
		block getProcessInstance(pinstance.processInstanceKey)
				.flatMap(expectNoIncident())
				.doOnNext { resp ->
					log.info("Checking for completion")
					with(resp) {
						state() == OperateClient.GetProcessInstanceResponse.State.COMPLETED
					}
					log.info("Instance is complete")
				}
				.retryWhen(waitForObjectRetrySpec)
	}
}
	
