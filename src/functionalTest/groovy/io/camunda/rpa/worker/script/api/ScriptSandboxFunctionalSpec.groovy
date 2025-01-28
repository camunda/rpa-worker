package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.ValidationFailureDto
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.robot.WorkspaceService
import org.spockframework.spring.SpringSpy
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class ScriptSandboxFunctionalSpec extends AbstractFunctionalSpec implements PublisherUtils {
	
	@SpringSpy
	WorkspaceService workspaceService

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
		r.result() == ExecutionResults.Result.PASS
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
		r.result() == ExecutionResults.Result.FAIL
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
		r.result() == ExecutionResults.Result.ERROR
		r.log().contains("contains no tests or tasks")
	}

	void "Cleans up workspaces after evaluate, leaving the last one"() {
		given:
		String script = '''\
*** Nothing ***
Nothing
'''
		and:
		Queue<Path> workspaces = new LinkedList<>()
		workspaceService.preserveLast(_) >> { Path workspace -> 
			workspaces.add(workspace)
			Mono<Void> r =  callRealMethod()
			r.block()
			return r
		}
		
		when:
		2.times {
			post()
					.uri("/script/evaluate")
					.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
							.script(script)
							.build()))
					.retrieve()
					.bodyToMono(EvaluateScriptResponse)
					.block(Duration.ofMinutes(1))
		}
		
		then:
		workspaces.size() == 2

		and: "First workspace deleted"
		Files.notExists(workspaces.remove())
		
		and: "Second workspace remains"
		Files.exists(workspaces.remove())
	}
}
