package io.camunda.rpa.worker.files;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactivefeign.webclient.WebReactiveFeign;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@RequiredArgsConstructor
class DocumentClientConfiguration {

	private final CamundaClientProperties camundaClientProperties;

	@Bean
	public DocumentClient documentClient(WebClient.Builder webClientBuilder) {
		return WebReactiveFeign
				.<DocumentClient>builder(webClientBuilder)
				.target(DocumentClient.class, camundaClientProperties.getZeebe().getBaseUrl() + "/v2/");
	}

	@Bean
	public Module documentModule() {
		ObjectMapper plainObjectMapper = new ObjectMapper();
		
		return new SimpleModule("zeebe-document", Version.unknownVersion()) {{
			addSerializer(ZeebeDocumentDescriptor.class, new JsonSerializer<>() {
				@Override
				public void serialize(ZeebeDocumentDescriptor value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
					Map<String, Object> map = plainObjectMapper.convertValue(value, new TypeReference<>() {});
					gen.writeObject(Stream.concat(
									map.entrySet().stream().filter(kv -> kv.getValue() != null),
									Stream.of(Map.entry("camunda.document.type", "camunda")))
							.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
				}
			});
		}};
	}
}
