package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.api.ValidationFailureDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

class LocalLibraryFunctionalSpec extends AbstractFunctionalSpec implements PublisherUtils {

	void "Deploy script fails on missing required data"() {
		when:
		ResponseEntity<ValidationFailureDto> resp = block post()
				.uri("/script/deploy")
				.body(BodyInserters.fromValue(DeployScriptRequest.builder().build()))
				.exchangeToMono(toResponseEntity(ValidationFailureDto))

		then:
		resp.statusCode == HttpStatus.UNPROCESSABLE_ENTITY
		resp.body.fieldErrors().size() == 2
		with(resp.body.fieldErrors()['id']) {
			code() == "NotBlank"
		}
		with(resp.body.fieldErrors()['script']) {
			code() == "NotBlank"
		}
	}

	void "Deploys scripts"() {
		when:
		ClientResponse response = block post()
				.uri("/script/deploy")
				.body(BodyInserters.fromValue(DeployScriptRequest.builder()
						.id("the-script-id")
						.script("Script content").build()))
				.exchangeToMono { cr -> Mono.just(cr) }

		then:
		response.statusCode() == HttpStatus.NO_CONTENT
	}
}
