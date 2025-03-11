package io.camunda.rpa.worker.secrets.vault

import io.camunda.rpa.worker.PublisherUtils
import org.springframework.context.ApplicationContext
import org.springframework.vault.core.ReactiveVaultOperations
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Function

class VaultBackendSpec extends Specification implements PublisherUtils {
	
	VaultProperties vaultProperties = new VaultProperties(["secrets/data/test/secret"])
	ReactiveVaultOperations vaultOps = Mock()
	Function<ApplicationContext, ReactiveVaultOperations> vaultOpsFactory = { vaultOps }
	
	@Subject
	VaultBackend backend = new VaultBackend(vaultProperties, null, vaultOpsFactory)
	
	
	void "Fetches secrets from client and returns merged map"() {
//		given:
//		secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
//				.secretId("some/secrets")
//				.build() as GetSecretValueRequest) >> CompletableFuture.completedFuture(GetSecretValueResponse.builder()
//				.secretString('{"foo": "bar", "bar": "baz"}')
//				.build())
//
//		secretsManagerClient.getSecretValue(GetSecretValueRequest
//				.builder()
//				.secretId("more/secrets")
//				.build() as GetSecretValueRequest) >> CompletableFuture.completedFuture(GetSecretValueResponse.builder()
//				.secretString('{"bar": "baz2", "bat": "tang"}')
//				.build())

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
