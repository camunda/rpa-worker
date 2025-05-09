package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters
import spock.lang.Ignore

import java.time.Duration

@Ignore("No cluster available")
class CamundaSecretsE2ESpec extends AbstractE2ESpec {
	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [
				CAMUNDA_RPA_SECRETS_BACKEND                : "camunda",

				CAMUNDA_CLIENT_MODE                        : "selfmanaged",
				CAMUNDA_CLIENT_AUTH_ISSUER                 : "https://login.cloud.dev.ultrawombat.com/oauth/token",
				CAMUNDA_CLIENT_ZEEBE_AUDIENCE              : "zeebe.dev.ultrawombat.com",
				CAMUNDA_RPA_SECRETS_CAMUNDA_TOKENAUDIENCE  : "secrets.dev.ultrawombat.com",
				CAMUNDA_RPA_ZEEBE_AUTHENDPOINT             : "https://login.cloud.dev.ultrawombat.com/oauth",
				CAMUNDA_RPA_SECRETS_CAMUNDA_SECRETSENDPOINT: "https://cluster-api.cloud.dev.ultrawombat.com",
				CAMUNDA_CLIENT_ZEEBE_ENABLED               : "false",

				CAMUNDA_CLIENT_AUTH_CLIENTID               : zeebeConfiguration.getEnv("CAMUNDA_E2E_SECRETS_CAMUNDA_CLIENTID"),
				CAMUNDA_CLIENT_AUTH_CLIENTSECRET           : zeebeConfiguration.getEnv("CAMUNDA_E2E_SECRETS_CAMUNDA_CLIENTSECRET"),
				CAMUNDA_CLIENT_CLUSTERID                   : zeebeConfiguration.getEnv("CAMUNDA_E2E_SECRETS_CAMUNDA_CLUSTERID"),
				CAMUNDA_CLIENT_REGION                      : "lpp-1",
		]
	}

	void "Secrets from the Camunda backend are available to the script"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
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
