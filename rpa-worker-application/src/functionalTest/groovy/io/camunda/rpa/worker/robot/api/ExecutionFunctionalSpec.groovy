package io.camunda.rpa.worker.robot.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClientResponseException

import java.time.Duration

class ExecutionFunctionalSpec extends AbstractFunctionalSpec {
	
	void "Returns not found when no matching execution"() {
		when:
		block post()
				.uri("/execution/doesnt-exist/abort")
				.body(BodyInserters.fromValue(new AbortExecutionRequest(null)))
				.retrieve()
				.toBodilessEntity()
		
		then:
		thrown(WebClientResponseException.NotFound)
	}
	
	void "Aborts process"() {
		when:
		EvaluateScriptResponse response = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script("""\
*** Settings ***
Library    Camunda
Library    RequestsLibrary

*** Tasks ***
Abort
    \${reqBody}=    Create Dictionary    silent=\${False}
    POST    http://127.0.0.1:${serverPort}/execution/%{RPA_WORKSPACE_ID}/abort    json=\${reqBody}
""")
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))
		
		then:
		response.result() == ExecutionResults.Result.ERROR
		response.log().contains("Execution terminated by signal")
	}

	void "Aborts process"(String silent, ExecutionResults.Result expectedResult, String expectedLog) {
		when:
		EvaluateScriptResponse response = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script("""\
*** Settings ***
Library    Camunda
Library    RequestsLibrary

*** Tasks ***
Abort
    \${reqBody}=    Create Dictionary    silent=\${${silent}}
    POST    http://127.0.0.1:${serverPort}/execution/%{RPA_WORKSPACE_ID}/abort    json=\${reqBody}
""")
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		response.result() == expectedResult
		response.log().contains(expectedLog)

		where:
		silent  || expectedResult                | expectedLog
		'True'  || ExecutionResults.Result.PASS  | "The execution was aborted"
		'False' || ExecutionResults.Result.ERROR | "Execution terminated by signal"
	}


}
