package io.camunda.rpa.worker.script

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateRawScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.PendingFeature

import java.time.Duration

class ContainerBrowserAutomationE2ESpec extends AbstractE2ESpec {

	@Override
	void startWorker() {}

	@Override
	void stopWorker() {}

	private WebClient $$webClient

	@Override
	@Delegate
	WebClient getWebClient() {
		if ( ! $$webClient)
			$$webClient = webClientBuilder.baseUrl("http://rpa-worker.local:36228").build()

		return $$webClient
	}

	@PendingFeature(reason = "Selenium version used by RPA libs too old, Selenium Manager cannot provision Firefox")
	void "Runs RPA Challenge in container"() {
		given:
		String scriptBody = getClass().getResource("/rpa_challenge.robot").text

		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateRawScriptRequest.builder()
						.script(scriptBody)
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
	}

	void "Runs RPA Challenge in container - Chrome"() {
		given:
		String scriptBody = getClass().getResource("/rpa_challenge_chrome.robot").text

		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateRawScriptRequest.builder()
						.script(scriptBody)
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
	}
}