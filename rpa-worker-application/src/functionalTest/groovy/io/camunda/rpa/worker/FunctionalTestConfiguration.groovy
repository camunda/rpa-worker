package io.camunda.rpa.worker

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.mock.env.MockPropertySource

import java.nio.file.Files

@Configuration
class FunctionalTestConfiguration {
	
	static class StaticPropertyProvidingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		@Override
		void initialize(ConfigurableApplicationContext applicationContext) {
			
			MockPropertySource properties = new MockPropertySource("mockProps")
					.withProperty("camunda.rpa.scripts.dir", Files.createTempDirectory("rpaScripts"))
					.withProperty("camunda.rpa.zeebe.auth-endpoint", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_AUTH_PORT}")
					.withProperty("camunda.rpa.zeebe.secrets.secrets-endpoint", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_SECRETS_PORT}")
					.withProperty("camunda.client.zeebe.base-url", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_API_PORT}")
			
			applicationContext.environment.propertySources.with {
				if(contains("secretsProps"))
					addAfter("secretsProps", properties)
				else
					addFirst(properties)
			}
		}
	}
	
}
