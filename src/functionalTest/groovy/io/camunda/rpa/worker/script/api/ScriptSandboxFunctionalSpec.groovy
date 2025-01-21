package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.ValidationFailureDto
import io.camunda.rpa.worker.robot.ExecutionResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

import java.time.Duration
import java.util.function.Function

class ScriptSandboxFunctionalSpec extends AbstractFunctionalSpec implements PublisherUtils {

	void "Evaluate script fails on missing required data"() {
		when:
		ResponseEntity<ValidationFailureDto> resp = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder().build()))
				.exchangeToMono(toResponseEntity(ValidationFailureDto))

		then:
		resp.statusCode == HttpStatus.UNPROCESSABLE_ENTITY
		resp.body.fieldErrors().size() == 1
		with(resp.body.fieldErrors()['script']) {
			code() == "NotBlank"
		}
	}
	
	void "Evaluates script, returns correct result object (Proc OK, Robot OK, Tasks OK)"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
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
		r.result() == ExecutionResult.Result.PASS
		r.log().contains("[STDOUT] Assert input variable")
		r.variables() == [anOutputVariable: 'output-variable-value']
	}
	
	void "Evaluates script, returns correct result object (Proc OK, Robot OK, Tasks FAIL)"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Tasks ***
Assert input variable
    Should Be Equal    ${thisWasNeverSet}    expected-input-variable-value
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResult.Result.FAIL
		r.log().contains('Variable \'${thisWasNeverSet}\' not found')
		r.variables() == [:]
	}

	void "Evaluates script, returns correct result object (Proc OK, Robot FAIL, Tasks FAIL)"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Nothing ***
Nothing
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.result() == ExecutionResult.Result.ERROR
		r.log().contains("[ ERROR ] Suite 'Script' contains no tests or tasks")
	}

	private static <T> Function<ClientResponse, Mono<ResponseEntity<T>>> toResponseEntity(Class<T> klass) {
		return (cr) -> cr.bodyToMono(klass)
				.map { str -> ResponseEntity.status(cr.statusCode()).body(str) }
	}
}
