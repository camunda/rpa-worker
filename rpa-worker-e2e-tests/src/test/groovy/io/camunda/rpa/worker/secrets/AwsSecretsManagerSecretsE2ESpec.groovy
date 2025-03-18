package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters

import java.time.Duration

class AwsSecretsManagerSecretsE2ESpec extends AbstractE2ESpec {
	@Override
	protected Map<String, String> getExtraEnvironment() {
		return [
				CAMUNDA_RPA_SECRETS_BACKEND    : "aws-secretsmanager",
				CAMUNDA_RPA_SECRETS_AWS_SECRETS: "test/secrets",

				AWS_ACCESS_KEY_ID              : System.getenv("CAMUNDA_E2E_SECRETS_AWS_ACCESSKEYID"),
				AWS_SECRET_ACCESS_KEY          : System.getenv("CAMUNDA_E2E_SECRETS_AWS_SECRETACCESSKEY"),
				AWS_REGION                     : "eu-west-2",

				CAMUNDA_CLIENT_ZEEBE_ENABLED   : "false",
		]
	}

	void "Secrets from the AWS Secrets Manager backend are available to the script"() {
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
