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
			applicationContext.getEnvironment().propertySources.addFirst(new MockPropertySource()
					.withProperty("camunda.rpa.scripts.dir", Files.createTempDirectory("rpaScripts"))
					.withProperty("camunda.rpa.zeebe.auth-endpoint", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_AUTH_PORT}")
					.withProperty("camunda.rpa.zeebe.secrets.secrets-endpoint", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_SECRETS_PORT}")
					.withProperty("camunda.client.zeebe.base-url", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_DOCUMENTS_PORT}")
					.withProperty("camunda.rpa.zeebe.documents.documents-endpoint", "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_DOCUMENTS_PORT}"))
		}
	}
	
}
