package io.camunda.rpa.worker.secrets.vault

import io.camunda.rpa.worker.AbstractFunctionalSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import org.springframework.vault.VaultException
import spock.lang.IgnoreIf

@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
class VaultNativeHintsHelper extends AbstractFunctionalSpec {
	
	@TestPropertySource(properties = [
			"camunda.rpa.secrets.backend=vault",
			"vault.uri=http://vault.local", 
			"vault.token=dev-token"
	])
	@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
	static class HappyPath extends AbstractFunctionalSpec {

		@Autowired
		ApplicationContext applicationContext

		void "Happy path"() {
			when:
			VaultProperties vaultProperties = new VaultProperties(["secret/data/test/secret"])
			Map<String, Object> secrets = block new VaultBackend(vaultProperties, applicationContext).getSecrets()

			then:
			! secrets.isEmpty()
		}
	}

	@TestPropertySource(properties = [
			"camunda.rpa.secrets.backend=vault",
			"vault.uri=http://vault.local",
			"vault.token=dev-token"
	])
	@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
	static class NoSecret extends AbstractFunctionalSpec {

		@Autowired
		ApplicationContext applicationContext

		void "No Secret"() {
			when:
			VaultProperties vaultProperties = new VaultProperties(["secret/data/test/fakesecret"])
			Map<String, Object> secrets = block new VaultBackend(vaultProperties, applicationContext).getSecrets()

			then:
			secrets.isEmpty()
		}
	}

	@TestPropertySource(properties = [
			"camunda.rpa.secrets.backend=vault",
			"vault.uri=http://vault.local",
			"vault.token=the-wrong-token"
	])
	@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
	static class BadCredentials extends AbstractFunctionalSpec {

		@Autowired
		ApplicationContext applicationContext

		void "Bad Credentials"() {
			when:
			VaultProperties vaultProperties = new VaultProperties(["secret/data/test/fakesecret"])
			Map<String, Object> secrets = block new VaultBackend(vaultProperties, applicationContext).getSecrets()

			then:
			thrown(VaultException)
		}
	}
}
