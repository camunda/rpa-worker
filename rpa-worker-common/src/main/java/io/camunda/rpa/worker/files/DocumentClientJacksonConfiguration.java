package io.camunda.rpa.worker.files;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@RequiredArgsConstructor
@Slf4j
class DocumentClientJacksonConfiguration {

	@Bean
	public JacksonModule documentModule() {
		ObjectMapper plainObjectMapper = new ObjectMapper();
		
		return new SimpleModule("zeebe-document", Version.unknownVersion()) {{
			addSerializer(ZeebeDocumentDescriptor.class, new ValueSerializer<>() {
				@Override
				public void serialize(ZeebeDocumentDescriptor value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
					Map<String, Object> map = plainObjectMapper.convertValue(value, new TypeReference<>() {});
					gen.writePOJO(Stream.concat(
									map.entrySet().stream().filter(kv -> kv.getValue() != null),
									Stream.of(Map.entry("camunda.document.type", "camunda")))
							.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
				}
			});
		}};
	}
}
