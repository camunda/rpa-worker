package io.camunda.rpa.worker.secrets.aws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.rpa.worker.secrets.SecretsBackend;
import io.camunda.rpa.worker.secrets.SecretsProperties;
import io.camunda.rpa.worker.util.MoreCollectors;
import io.vavr.Lazy;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class SecretsManagerBackend implements SecretsBackend {
	
	private static final Supplier<SecretsManagerAsyncClient> defaultSecretsManagerClientFactory = SecretsManagerAsyncClient::create;
	
	private final SecretsProperties secretsProperties;
	private final ObjectMapper objectMapper;
	private final SecretsManagerAsyncClient secretsManagerClient;

	@Autowired
	public SecretsManagerBackend(SecretsProperties secretsProperties, ObjectMapper objectMapper) {
		this(secretsProperties, objectMapper, defaultSecretsManagerClientFactory);
	}

	SecretsManagerBackend(SecretsProperties secretsProperties, ObjectMapper objectMapper, Supplier<SecretsManagerAsyncClient> secretsManagerClientFactory) {
		this.secretsProperties = secretsProperties;
		this.objectMapper = objectMapper;
		this.secretsManagerClient = Lazy.val(secretsManagerClientFactory, SecretsManagerAsyncClient.class);
	}

	@Override
	public String getKey() {
		return "aws-secretsmanager";
	}

	@Override
	public Mono<Map<String, Object>> getSecrets() {
		return Flux.fromIterable(secretsProperties.secrets())
				.map(secretName -> GetSecretValueRequest.builder().secretId(secretName).build())

				.flatMapSequential(req ->
						Mono.fromCompletionStage(secretsManagerClient.getSecretValue(req)))
				.map(resp -> Try.of(() -> objectMapper.readValue(
						resp.secretString(),
						new TypeReference<Map<String, Object>>() {})).get())

				.flatMapIterable(Map::entrySet)
				.collect(MoreCollectors.toSequencedMap(
						Map.Entry::getKey, 
						Map.Entry::getValue, 
						MoreCollectors.MergeStrategy.rightPrecedence()));
	}
}
