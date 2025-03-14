package io.camunda.rpa.worker.secrets.vault

import io.camunda.rpa.worker.PublisherUtils
import org.springframework.context.ApplicationContext
import org.springframework.vault.core.ReactiveVaultOperations
import org.springframework.vault.support.VaultResponse
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Function

class VaultBackendSpec extends Specification implements PublisherUtils {
	
	VaultProperties vaultProperties = new VaultProperties(["secrets/data/some/secrets", "secrets/data/more/secrets"])
	ReactiveVaultOperations vaultOps = Mock()
	Function<ApplicationContext, ReactiveVaultOperations> vaultOpsFactory = { vaultOps }
	
	@Subject
	VaultBackend backend = new VaultBackend(vaultProperties, null, vaultOpsFactory)
	
	
	void "Fetches secrets from client and returns merged map"() {
		given:
		vaultOps.read("secrets/data/some/secrets") >> Mono.just(new VaultResponse().tap {
			data = [foo: 'bar', bar: 'baz']
		})
		vaultOps.read("secrets/data/more/secrets") >> Mono.just(new VaultResponse().tap {
			data = [data: [bar: 'baz2', bat: 'tang']]
		})

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
