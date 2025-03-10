package io.camunda.rpa.worker.secrets

import io.camunda.rpa.worker.AbstractFunctionalSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.env.MockPropertySource
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
class NoSecretsFunctionalSpec extends AbstractFunctionalSpec {
	
	@Autowired
	SecretsService secretsService
	
	void "Returns empty secrets when not enabled"() {
		when:
		Map<String, String> r = block secretsService.getSecrets()
		
		then:
		r == [:]
	}

	static class StaticPropertyProvidingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		@Override
		void initialize(ConfigurableApplicationContext applicationContext) {
			applicationContext.getEnvironment().propertySources.addFirst(new MockPropertySource("secretsProps")
					.withProperty("camunda.rpa.secrets.backend", "none"))
		}
	}
}
