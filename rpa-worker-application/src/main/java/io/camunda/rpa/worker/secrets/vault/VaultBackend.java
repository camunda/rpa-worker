package io.camunda.rpa.worker.secrets.vault;

import io.camunda.rpa.worker.secrets.SecretsBackend;
import io.camunda.rpa.worker.util.MoreCollectors;
import io.vavr.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.ReactiveVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.client.WebClientFactory;
import org.springframework.vault.config.AbstractReactiveVaultConfiguration;
import org.springframework.vault.config.EnvironmentVaultConfiguration;
import org.springframework.vault.core.ReactiveVaultOperations;
import org.springframework.vault.core.ReactiveVaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

@Component
public class VaultBackend implements SecretsBackend {
	
	private static final Function<ApplicationContext, ReactiveVaultOperations> defaultVaultOpsFactory = VaultBackend::createVaultTemplate;
	
	private final VaultProperties vaultProperties;
	private final ReactiveVaultOperations vaultOperations;

	@Autowired
	VaultBackend(VaultProperties vaultProperties, ApplicationContext applicationContext) {
		this(vaultProperties, applicationContext, defaultVaultOpsFactory);
	}

	VaultBackend(VaultProperties vaultProperties, ApplicationContext applicationContext, Function<ApplicationContext, ReactiveVaultOperations> vaultOpsFactory) {
		this.vaultProperties = vaultProperties;
		this.vaultOperations = Lazy.val(() -> vaultOpsFactory.apply(applicationContext), ReactiveVaultOperations.class);
	}

	@Override
	public String getKey() {
		return "vault";
	}

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Map<String, Object>> getSecrets() {
		return Flux.fromIterable(vaultProperties.secrets())
				.flatMapSequential(vaultOperations::read)
				.mapNotNull(VaultResponseSupport::getData)
				.map(data -> (Map<String, Object>) data.getOrDefault("data", data))

				.flatMapIterable(Map::entrySet)
				.collect(MoreCollectors.toSequencedMap(
						Map.Entry::getKey, 
						Map.Entry::getValue, 
						MoreCollectors.MergeStrategy.rightPrecedence()));
	}
	
	private static ReactiveVaultOperations createVaultTemplate(ApplicationContext applicationContext) {
		EnvironmentVaultConfiguration envConfig = new EnvironmentVaultConfiguration();
		envConfig.setApplicationContext(applicationContext);
		AbstractReactiveVaultConfiguration providerConfig = new AbstractReactiveVaultConfiguration() {
			@Override
			public VaultEndpoint vaultEndpoint() {
				return envConfig.vaultEndpoint();
			}

			@Override
			public ClientAuthentication clientAuthentication() {
				return envConfig.clientAuthentication();
			}

			@Override
			public ReactiveVaultTemplate reactiveVaultTemplate() {
				return new ReactiveVaultTemplate(webClientBuilder(reactiveVaultEndpointProvider(), clientHttpConnector()),
						reactiveSessionManager());
			}

			@Override
			protected WebClientBuilder webClientBuilder(ReactiveVaultEndpointProvider endpointProvider, ClientHttpConnector httpConnector) {
				return WebClientBuilder.builder()
						.endpointProvider(endpointProvider)
						.httpConnector(httpConnector);
			}

			@Override
			protected WebClientFactory getWebClientFactory() {
				return webClientFactory();
			}

			@Override
			protected ThreadPoolTaskScheduler getVaultThreadPoolTaskScheduler() {
				ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

				threadPoolTaskScheduler.setThreadNamePrefix("spring-vault-ThreadPoolTaskScheduler-");
				threadPoolTaskScheduler.setDaemon(true);
				return threadPoolTaskScheduler;
			}
		};
		providerConfig.setApplicationContext(applicationContext);
		return providerConfig.reactiveVaultTemplate();
	}
}
