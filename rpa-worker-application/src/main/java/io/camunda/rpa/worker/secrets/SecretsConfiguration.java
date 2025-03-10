package io.camunda.rpa.worker.secrets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Set;

@Configuration
class SecretsConfiguration {
	
	private static final Set<String> NO_SECRETS = Set.of("none", "off", "false", "disabled");
	
	@Bean
	public SecretsService secretsService(
			SecretsProperties secretsProperties,
			ObjectProvider<SecretsBackend> backends) {
		
		SecretsBackend targetBackend = null;
		
		if(StringUtils.hasText(secretsProperties.backend())
			&& ! NO_SECRETS.contains(secretsProperties.backend().toLowerCase()))
			targetBackend = backends.stream()
					.filter(it -> it.getKey().equals(secretsProperties.backend().toLowerCase()))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("No secrets backend found for " + secretsProperties.backend()));
		
		return new SecretsService(targetBackend);
	}
}
