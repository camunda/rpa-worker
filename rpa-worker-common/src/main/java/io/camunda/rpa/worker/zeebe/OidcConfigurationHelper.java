package io.camunda.rpa.worker.zeebe;

import io.camunda.client.spring.properties.CamundaClientAuthProperties;
import io.camunda.client.spring.properties.CamundaClientProperties;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Optional;

class OidcConfigurationHelper {

	static URI getTokenUrl(ZeebeProperties zeebeProperties, CamundaClientProperties camundaClientProperties, WebClient webClient) {
		if(zeebeProperties.authMethod() != ZeebeProperties.AuthMethod.TOKEN 
				|| camundaClientProperties.getAuth().getMethod() != CamundaClientAuthProperties.AuthMethod.oidc)
			return URI.create("http://none");

		return Optional.ofNullable(camundaClientProperties.getAuth().getTokenUrl())
				.orElseGet(() -> discoverTokenUrl(camundaClientProperties, webClient));
	}
	
	private static URI discoverTokenUrl(CamundaClientProperties camundaClientProperties, WebClient webClient) {
		URI wellKnownUrl = URI.create("%s".formatted(camundaClientProperties.getAuth().getIssuerUrl())
						+ (camundaClientProperties.getAuth().getIssuerUrl().getPath().endsWith("/") ? "" : "/"))
				.resolve(".well-known/openid-configuration");

		return webClient.get().uri(wellKnownUrl).retrieve().bodyToMono(WellKnownConfiguration.class).block().tokenEndpoint();
	}

	record WellKnownConfiguration(URI tokenEndpoint) { }
}
