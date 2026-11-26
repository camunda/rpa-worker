package io.camunda.rpa.worker.zeebe

import io.camunda.client.api.command.ThrowErrorCommandStep1
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.secrets.SecretsService
import org.spockframework.spring.SpringBean
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono

import java.time.Duration

@TestPropertySource(properties = "camunda.rpa.sandbox.enabled=false")
class AuthenticatedCallbacksFunctionalSpec extends AbstractScriptRepositoryProvidingZeebeFunctionalSpec {

	@SpringBean
	SecretsService secretsService = Stub() {
		getSecrets() >> Mono.just([:])
	}

	@Override
	List<RobotScript> getScripts() {
		return [RobotScript.builder().id("throw").body('''\
*** Settings ***
Library    Camunda

*** Tasks ***
Report error
    Set Output Variable    callbackWorked    ${True}
    Throw BPMN Error    EXPECTED_ERROR    Expected message
''').build()]
	}

	void "Production Python keywords authenticate with evaluation disabled"() {
		when:
		zeebeJobService.handleJob(anRpaJob([:], "throw", [:], 123)).block(Duration.ofSeconds(15))

		then:
		1 * camundaClient.newThrowErrorCommand(123L) >> Mock(ThrowErrorCommandStep1) {
			1 * errorCode("EXPECTED_ERROR") >> Mock(ThrowErrorCommandStep1.ThrowErrorCommandStep2) {
				1 * errorMessage("Expected message") >> it
				1 * send()
			}
		}
		0 * camundaClient.newCompleteCommand(_)
		0 * camundaClient.newFailCommand(_)
	}
}
