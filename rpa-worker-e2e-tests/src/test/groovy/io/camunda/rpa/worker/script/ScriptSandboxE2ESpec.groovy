package io.camunda.rpa.worker.script

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateRawScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateRichScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import io.camunda.rpa.worker.zeebe.RpaResource
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters

import java.time.Duration

class ScriptSandboxE2ESpec extends AbstractE2ESpec {

	@Override
	Map<String, String> getEnvironment() {
		return [:]
	}

	void "Evaluates script and returns result"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateRawScriptRequest.builder()
						.script('''\
*** Settings ***
Library    Camunda

*** Tasks ***
Assert input variable
    Should Be Equal    ${expectedInputVariable}    expected-input-variable-value

Set an output variable
    Set Output Variable     anOutputVariable      output-variable-value
''')
						.variables([expectedInputVariable: 'expected-input-variable-value'])
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
		r.log().contains("[STDOUT] Assert input variable")
		r.variables() == [anOutputVariable: 'output-variable-value']
	}

	void "Evaluates resource and returns result"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.header(HttpHeaders.CONTENT_TYPE, "application/vnd.camunda.rpa+json")
				.body(BodyInserters.fromValue(EvaluateRichScriptRequest.builder()
						.rpa(RpaResource.builder().script('''\
*** Settings ***
Library    Camunda
Library    OperatingSystem

*** Tasks ***
Assert input variable
    Should Be Equal    ${expectedInputVariable}    expected-input-variable-value

Set an output variable
    Set Output Variable     anOutputVariable      output-variable-value
    
Assert Additional Files
    ${fileContents1}=    Get File    one.resource
    Should Be Equal    ${fileContents1}    one.resource content
    
    ${fileContents1}=    Get File    two/three.resource
    Should Be Equal    ${fileContents1}    three.resource content
''')
								.file('one.resource', 'H4sIAAAAAAAAA8vPS9UrSi3OLy1KTlVIzs8rSc0rAQDo66f1FAAAAA==')
								.file('two/three.resource', 'H4sIAAAAAAAAAyvJKEpN1StKLc4vLUpOVUjOzytJzSsBADv+Z18WAAAA')
								.build())
						.variables([expectedInputVariable: 'expected-input-variable-value'])
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResults.Result.PASS
		r.log().contains("[STDOUT] Assert input variable")
		r.variables() == [anOutputVariable: 'output-variable-value']
	}
}
