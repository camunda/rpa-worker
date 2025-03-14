package io.camunda.rpa.worker.secrets.aws

import com.fasterxml.jackson.databind.ObjectMapper
import io.camunda.rpa.worker.PublisherUtils
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class SecretsManagerBackendSpec extends Specification implements PublisherUtils {
	
	SecretsManagerProperties secretsManagerProperties = new SecretsManagerProperties(["some/secrets", "more/secrets"])
	SecretsManagerAsyncClient secretsManagerClient = Mock()
	ObjectMapper objectMapper = new ObjectMapper()
	Supplier<SecretsManagerAsyncClient> secretsManagerClientFactory = { secretsManagerClient }
	
	@Subject
	SecretsManagerBackend backend = new SecretsManagerBackend(secretsManagerProperties, objectMapper, secretsManagerClientFactory)
	
	
	void "Fetches secrets from client and returns merged map"() {
		given:
		secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
				.secretId("some/secrets")
				.build() as GetSecretValueRequest) >> CompletableFuture.completedFuture(GetSecretValueResponse.builder()
				.secretString('{"foo": "bar", "bar": "baz"}')
				.build())

		secretsManagerClient.getSecretValue(GetSecretValueRequest
				.builder()
				.secretId("more/secrets")
				.build() as GetSecretValueRequest) >> CompletableFuture.completedFuture(GetSecretValueResponse.builder()
				.secretString('{"bar": "baz2", "bat": "tang"}')
				.build())

		when:
		Map<String, Object> secrets = block backend.getSecrets()
		
		then:
		secrets == [
		        foo: "bar",
				bar: "baz2",
				bat: "tang"
		]
	}
}
