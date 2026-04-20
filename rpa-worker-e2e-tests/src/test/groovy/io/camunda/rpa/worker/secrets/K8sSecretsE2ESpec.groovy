package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateRawScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters

import java.time.Duration

class K8sSecretsE2ESpec extends AbstractE2ESpec {
	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [
				CAMUNDA_RPA_SECRETS_BACKEND    : "k8s",
				CAMUNDA_RPA_SECRETS_K8S_SECRETS: "test-secret@secrets-test",

				KUBECONFIG                     : "/etc/rancher/k3s/k3s.yaml",

				CAMUNDA_CLIENT_ZEEBE_ENABLED   : "false",
		]
	}

	void "Secrets from the K8S backend are available to the script"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateRawScriptRequest.builder()
						.script('''\
*** Settings ***
Library    Camunda

*** Tasks ***
Assert secrets
    Should Be Equal    ${SECRETS['foo']}    bar
    Should Be Equal    ${SECRETS['baz']}    bat
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
		r.log().contains("[STDOUT] Assert secrets")
	}
}
