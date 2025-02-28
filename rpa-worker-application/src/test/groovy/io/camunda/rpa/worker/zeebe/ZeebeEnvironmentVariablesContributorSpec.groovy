package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.robot.EnvironmentVariablesContributor
import io.camunda.zeebe.client.api.response.ActivatedJob
import spock.lang.Specification
import spock.lang.Subject

class ZeebeEnvironmentVariablesContributorSpec extends Specification implements PublisherUtils {

	@Subject
	EnvironmentVariablesContributor contributor = new ZeebeEnvironmentVariablesContributor()

	void "Returns empty variables when no Zeebe job in context"() {
		expect:
		block(contributor.getEnvironmentVariables(null, null)).isEmpty()
	}

	void "Returns correct environment variables for Zeebe job"() {
		given:
		ActivatedJob job = Stub() {
			getKey() >> 123
			getType() >> "job-type"
			getBpmnProcessId() >> "456"
			getProcessInstanceKey() >> 789
		}

		when:
		Map<String, String> vars = block contributor.getEnvironmentVariables(null, null)
				.contextWrite { ctx -> ctx.put(ActivatedJob, job) }

		then:
		vars == [
				RPA_ZEEBE_JOB_KEY             : "123",
				RPA_ZEEBE_JOB_TYPE            : "job-type",
				RPA_ZEEBE_BPMN_PROCESS_ID     : "456",
				RPA_ZEEBE_PROCESS_INSTANCE_KEY: "789"
		]
	}
}
