package io.camunda.rpa.worker.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("camunda.rpa.api")
public record ApiSecurityProperties(
		@DefaultValue("") String token,
		List<String> allowedHosts) {

	public ApiSecurityProperties {
		if (token != null && !token.matches("(?:|[A-Za-z0-9_-]{32,})"))
			throw new IllegalArgumentException("camunda.rpa.api.token must be empty or at least 32 letters, digits, underscores, or hyphens");
		allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
	}

	@Override
	public String toString() {
		return "ApiSecurityProperties[token=<redacted>, allowedHosts=" + allowedHosts + "]";
	}
}
