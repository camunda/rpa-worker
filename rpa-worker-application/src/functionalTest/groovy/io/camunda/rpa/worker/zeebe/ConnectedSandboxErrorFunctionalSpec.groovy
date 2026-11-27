package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import io.camunda.rpa.worker.secrets.SecretsService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Mono

import java.nio.file.Paths
import java.time.Duration

class ConnectedSandboxErrorFunctionalSpec extends AbstractFunctionalSpec {

	@Autowired
	ZeebeClientStatus zeebeClientStatus

	@SpringBean
	SecretsService secretsService = Stub() {
		getSecrets() >> Mono.just(Collections.emptyMap())
	}

	void "Sandbox BPMN errors are stubbed even when a cluster client is enabled"() {
		given:
		assert zeebeClientStatus.isZeebeClientEnabled()

		when:
		def response = post().uri("/script/evaluate")
				.bodyValue([script: """\
*** Settings ***
Library    Camunda

*** Tasks ***
Task
    Throw BPMN Error    SANDBOX_ERROR
"""])
				.retrieve().bodyToMono(EvaluateScriptResponse).block(Duration.ofMinutes(1))

		then:
		response.result() == ExecutionResults.Result.FAIL
		response.log().contains("SANDBOX_ERROR")
		def output = Paths.get(response.log().find(/(?<=Output:\s+)([\S].*)\.xml/)).getText("UTF-8")
		output.contains("STUB: Zeebe")
		output.contains("newThrowErrorCommand")
	}
}
