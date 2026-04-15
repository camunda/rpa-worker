package io.camunda.rpa.worker.zeebe;

import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.rpa.worker.net.WebClientProvisioner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.net.URI;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@ConditionalOnCamundaClientEnabled
class ZeebeClientsConfiguration {

	private final ZeebeProperties zeebeProperties;
	private final CamundaClientProperties camundaClientProperties;
	private final ObjectProvider<ZeebeAuthenticationService> zeebeAuthenticationService;
	private final ZeebeAuthProperties zeebeAuthProperties;

	@Bean
	public AuthClient authClient(WebClientProvisioner webClientProvisioner, ObjectMapper objectMapper) {
		AuthClient.InternalClient client = HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClientProvisioner.webClient(b -> b
						.baseUrl(OidcConfigurationHelper.getTokenUrl(zeebeProperties, camundaClientProperties, webClientProvisioner.webClient()).toString()))))
				.build()
				.createClient(AuthClient.InternalClient.class);

		return auth -> client.authenticate(
				MultiValueMap.fromSingleValue(objectMapper.convertValue(auth, new TypeReference<>() {})));
	}

	@Bean
	public C8RunAuthClient c8RunAuthClient(WebClientProvisioner webClientProvisioner) {
		return HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClientProvisioner.webClient(b -> b
						.baseUrl(camundaClientProperties.getRestAddress().toString()))))
				.build()
				.createClient(C8RunAuthClient.class);
	}

	@Bean
	public ResourceClient resourceClient(WebClientProvisioner webClientProvisioner) {
		Mono<String> authenticator = zeebeAuthenticationService.getObject().getAuthToken(
				zeebeAuthProperties.clientId(),
				zeebeAuthProperties.clientSecret(),
				camundaClientProperties.getAuth().getAudience());

		return HttpServiceProxyFactory
				.builderFor(WebClientAdapter.create(webClientProvisioner.webClient(b -> b
						.baseUrl(camundaClientProperties.getRestAddress() + "/v2/")
						.filter(zeebeProperties.authMethod().interceptor(authenticator)))))
				.build()
				.createClient(ResourceClient.class);
	}

	@Bean
	public JacksonModule authModule() {
		return new SimpleModule("zeebe-auth", Version.unknownVersion()) {{
			addSerializer(AuthClient.AuthenticationRequest.class, new ValueSerializer<>() {
				@Override
				public void serialize(AuthClient.AuthenticationRequest value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
					gen.writePOJO(Map.of("client_id", value.clientId(),
							"client_secret", value.clientSecret(),
							"audience", value.audience(),
							"grant_type", value.grantType()));
				}
			});

			addDeserializer(AuthClient.AuthenticationResponse.class, new ValueDeserializer<>() {
				@Override
				public AuthClient.AuthenticationResponse deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
					ObjectReadContext codec = p.objectReadContext();
					Map<String, String> map = codec.readValue(p, new TypeReference<>() {});
					return new AuthClient.AuthenticationResponse(
							map.get("access_token"),
							Integer.parseInt(map.get("expires_in")));
				}
			});
			
			addDeserializer(OidcConfigurationHelper.WellKnownConfiguration.class, new ValueDeserializer<>() {
				@Override
				public OidcConfigurationHelper.WellKnownConfiguration deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
					ObjectReadContext codec = p.objectReadContext();
					Map<String, Object> map = codec.readValue(p, new TypeReference<>() {});
					return new OidcConfigurationHelper.WellKnownConfiguration(
							URI.create(map.get("token_endpoint").toString()));
				}
			});
		}};
	}
}
