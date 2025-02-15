package io.camunda.rpa.worker.zeebe;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.spring.client.actuator.ZeebeClientHealthIndicator;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.webclient.WebReactiveFeign;

import java.io.IOException;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
class ZeebeConfiguration {

	private final ZeebeProperties zeebeProperties;
	private final CamundaClientProperties camundaClientProperties;
	
	@Bean
	public AuthClient authClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
		AuthClient.InternalClient target = WebReactiveFeign
				.<AuthClient.InternalClient>builder(webClientBuilder)
				.target(AuthClient.InternalClient.class, zeebeProperties.authEndpoint().toString());
		
		return auth -> target.authenticate(
				objectMapper.convertValue(auth, new TypeReference<>() {}));
	}
	
	@Bean
	public ResourceClient resourceClient(WebClient.Builder webClientBuilder) {
		return WebReactiveFeign
				.<ResourceClient>builder(webClientBuilder)
				.target(ResourceClient.class, camundaClientProperties.getZeebe().getBaseUrl() + "/v2/");
	}

	@Bean
	public Module authModule() {
		return new SimpleModule("zeebe-auth", Version.unknownVersion()) {{
			addSerializer(AuthClient.AuthenticationRequest.class, new JsonSerializer<>() {
				@Override
				public void serialize(AuthClient.AuthenticationRequest value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
					gen.writeObject(Map.of("client_id", value.clientId(),
							"client_secret", value.clientSecret(),
							"audience", value.audience(),
							"grant_type", value.grantType()));
				}
			});

			addDeserializer(AuthClient.AuthenticationResponse.class, new JsonDeserializer<>() {
				@Override
				public AuthClient.AuthenticationResponse deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
					ObjectMapper codec = (ObjectMapper) p.getCodec();
					Map<String, String> map = codec.readValue(p, new TypeReference<>() {
					});
					return new AuthClient.AuthenticationResponse(
							map.get("access_token"),
							Integer.parseInt(map.get("expires_in")));
				}
			});
		}};
	}
	
	@Bean
	public BeanPostProcessor zeebeHealthCheckBeanPostProcessor(CamundaClientProperties camundaClientProperties) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if ( ! camundaClientProperties.getZeebe().getEnabled()
						&& bean instanceof ZeebeClientHealthIndicator)
					return new ZeebeClientHealthIndicator(null) {
						@Override
						protected void doHealthCheck(Health.Builder builder) {}
					};

				return bean;
			}
		};
	}
}
