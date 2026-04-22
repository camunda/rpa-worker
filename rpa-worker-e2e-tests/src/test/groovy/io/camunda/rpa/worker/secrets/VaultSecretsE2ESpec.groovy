package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateRawScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters

import java.time.Duration

class VaultSecretsE2ESpec extends AbstractE2ESpec {
	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [
				CAMUNDA_RPA_SECRETS_BACKEND      : "vault",
				CAMUNDA_RPA_SECRETS_VAULT_SECRETS: "secret/data/test/secret",

				VAULT_URI                        : "http://vault.local",
				VAULT_TOKEN                      : "root",

				CAMUNDA_CLIENT_ZEEBE_ENABLED     : "false",
		]
	}

	void "Secrets from the Vault backend are available to the script"() {
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
