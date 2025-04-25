package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters
import spock.lang.Ignore

import java.time.Duration

abstract class SmokeTestsE2ESpec extends AbstractE2ESpec {

	@Ignore("Selenium version used by RPA libs too old, Selenium Manager cannot provision Firefox")
	void "Runs the RPA Challenge"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(evaluateScriptRequest("rpa_challenge")))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))
		
		then:
		r.result() == ExecutionResults.Result.PASS
	}

	void "Runs the RPA Challenge - Chrome"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(evaluateScriptRequest("rpa_challenge_chrome")))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
	}

	private static evaluateScriptRequest(String scriptName) {
		String script = SmokeTestsE2ESpec.classLoader.getResource(scriptName + ".robot").text
		return EvaluateScriptRequest.builder()
				.script(script)
				.build()
	}
	
	static class PythonSmokeTestsE2ESpec extends SmokeTestsE2ESpec {
		@Override
		Map<String, String> getEnvironment() {
			return ['camunda.rpa.python-runtime.type': 'python']
		}
	}

	static class StaticSmokeTestsE2ESpec extends SmokeTestsE2ESpec {
		@Override
		Map<String, String> getEnvironment() {
			return ['camunda.rpa.python-runtime.type': 'static']
		}
	}
}
