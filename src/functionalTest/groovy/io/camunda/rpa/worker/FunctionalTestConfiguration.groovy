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
			applicationContext.getEnvironment().propertySources.addLast(new MockPropertySource()
					.withProperty("camunda.rpa.scripts.dir", Files.createTempDirectory("rpaScripts")))
		}
	}
	
}
