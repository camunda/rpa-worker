package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.ValidationFailureDto
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.workspace.WorkspaceCleanupService
import org.spockframework.spring.SpringSpy
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScriptSandboxFunctionalSpec extends AbstractFunctionalSpec implements PublisherUtils {
	
	@SpringSpy
	WorkspaceCleanupService workspaceCleanupService

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
		CountDownLatch latch = new CountDownLatch(2)
		Queue<Path> workspaces = new LinkedList<>()
		workspaceCleanupService.preserveLast(_) >> { Path workspace -> 
			workspaces.add(workspace)
			Mono<Void> r = callRealMethod()
			r.doFinally { latch.countDown() }.subscribe()
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
		latch.awaitRequired(40, TimeUnit.SECONDS)
		
		then:
		workspaces.size() == 2

		and: "First workspace deleted"
		Files.notExists(workspaces.remove())
		
		and: "Second workspace remains"
		Files.exists(workspaces.remove())
	}

	void "Serves workspace files after run"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Assert input variable
    Create File    outputs/file1.txt    File 1 contents
    Create File    outputs/file2.xlsx    File 2 contents
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		r.workspace().keySet().find { it.endsWith("/file1.txt") }
		r.workspace().values().find { it.toString().endsWith("/file2.xlsx?attachment") }

		when:
		String file1 = block get()
				.uri(r.workspace().entrySet().find { kv -> kv.key.endsWith("/file1.txt") }.value.toString())
				.exchangeToMono { cr -> cr.bodyToMono(String) }
		
		then:
		file1 == "File 1 contents"

		when:
		ClientResponse response = block get()
				.uri(r.workspace().entrySet().find { kv -> kv.key.endsWith("/file2.xlsx") }.value.toString())
				.exchangeToMono(cr -> Mono.just(cr))

		then:
		with(response.headers().asHttpHeaders()) {
			getFirst(HttpHeaders.CONTENT_TYPE) == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
			getFirst(HttpHeaders.CONTENT_LENGTH) == "15"
			getContentDisposition().type == "attachment"
			getContentDisposition().filename == "file2.xlsx"
		}
	}
	
	void "Not found when workspace does not exist"() {
		when:
		ClientResponse response = block get()
				.uri("/workspace/fake-workspace/file.txt")
				.exchangeToMono { cr -> Mono.just(cr) }
		
		then:
		response.statusCode() == HttpStatus.NOT_FOUND
	}

	void "Not found when workspace file does not exist"() {
		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Assert input variable
    Create File    outputs/file1.txt    File 1 contents
    Create File    outputs/file2.xlsx    File 2 contents
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		and:
		ClientResponse response2 = block get().uri(response.workspace().entrySet().first().value.resolve("fake-file.txt").toString())
				.exchangeToMono { cr -> Mono.just(cr) }
		
		then:
		response2.statusCode() == HttpStatus.NOT_FOUND
	}

	void "Not found when not a workspace file"() {
		when:
		EvaluateScriptResponse response = block post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    OperatingSystem

*** Tasks ***
Assert input variable
    Create File    outputs/file1.txt    File 1 contents
    Create File    outputs/file2.xlsx    File 2 contents
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)

		and:
		ClientResponse response2 = block get().uri(response.workspace().entrySet().first().value.resolve("../").toString() + "../../somefile.txt")
				.exchangeToMono { cr -> Mono.just(cr) }

		then:
		response2.statusCode() == HttpStatus.NOT_FOUND
	}
}
