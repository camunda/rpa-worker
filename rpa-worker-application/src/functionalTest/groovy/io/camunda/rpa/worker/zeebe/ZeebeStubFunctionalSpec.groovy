package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.script.api.EvaluateScriptRequest
import io.camunda.rpa.worker.script.api.EvaluateScriptResponse
import io.camunda.rpa.worker.secrets.SecretsService
import org.spockframework.spring.SpringBean
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono

import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.Duration

@TestPropertySource(properties = ["camunda.client.zeebe.enabled=false"])
class ZeebeStubFunctionalSpec extends AbstractFunctionalSpec implements PublisherUtils {

	@SpringBean
	SecretsService secretsService = Stub() {
		getSecrets() >> Mono.just(Collections.emptyMap())
	}

	void "Returns correct stubbed response for Upload Documents"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    Camunda
Library    OperatingSystem

*** Tasks ***
Tasks
    Create File    some-file.txt
    Upload Documents    some-file.txt
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		getLog(r).contains('''\
STUB: DocumentClient→uploadDocument
{
    "files": "some-file.txt"
}''')
		
		and:
		r.result() == ExecutionResults.Result.PASS
	}

	void "Returns correct stubbed response for Download Documents"() {
		given:
		ZeebeDocumentDescriptor aDocument = new ZeebeDocumentDescriptor(
				"store-id", 
				"document-id", 
				new ZeebeDocumentDescriptor.Metadata(
						null, 
						"file-name.txt",
						null, 
						null, 
						null,  
						null, 
						null),
				"hash")
		
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    Camunda
Library    OperatingSystem

*** Tasks ***
Tasks
    Download Documents    ${doc}
''')
						.variables(doc: aDocument)
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		getLog(r).contains('''\
STUB: DocumentClient→getDocument
{
    "file-name.txt": {
        "metadata": {
            "fileName": "file-name.txt"
        },
        "camunda.document.type": "camunda",
        "documentId": "document-id",
        "storeId": "store-id",
        "contentHash": "hash"
    }
}''')

		and:
		r.log().contains("Cannot continue after stub call to Download Documents")
		r.result() == ExecutionResults.Result.FAIL
	}

	void "Returns correct stubbed response for Throw BPMN Error"() {
		when:
		EvaluateScriptResponse r = post()
				.uri("/script/evaluate")
				.body(BodyInserters.fromValue(EvaluateScriptRequest.builder()
						.script('''\
*** Settings ***
Library    Camunda

*** Tasks ***
Tasks
    Throw BPMN Error    THE_ERROR
''')
						.build()))
				.retrieve()
				.bodyToMono(EvaluateScriptResponse)
				.block(Duration.ofMinutes(1))

		then:
		getLog(r).contains('''\
STUB: Zeebe→newThrowErrorCommand
{
    "errorCode": "THE_ERROR"
}''')

		and:
		r.log().contains("THE_ERROR")
		r.result() == ExecutionResults.Result.FAIL
	}

	private static String getLog(EvaluateScriptResponse response) {
		Paths.get(response.log().find(/(?<=Output:\s+)([\S].*)\.xml/)).getText(StandardCharsets.UTF_8.name()).replaceAll("\r", "")
	}
}
