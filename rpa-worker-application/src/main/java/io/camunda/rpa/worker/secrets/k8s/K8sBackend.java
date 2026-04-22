package io.camunda.rpa.worker.secrets.k8s;

import io.camunda.rpa.worker.secrets.SecretsBackend;
import io.camunda.rpa.worker.util.MoreCollectors;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.vavr.Lazy;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.camunda.rpa.worker.util.MoreCollectors.MergeStrategy.rightPrecedence;

@Slf4j
@Component
public class K8sBackend implements SecretsBackend {
	
	private static final Pattern SECRET_REF_PATTERN = Pattern.compile("^([^@]+)@([^@]+)$");
	private static final Function<ApplicationContext, KubernetesClient> defaultKubernetesClientFactory = K8sBackend::createKubernetesClient;
	
	private final K8sProperties k8sProperties;
	private final KubernetesClient kubernetesClient;

	@Autowired
	K8sBackend(K8sProperties k8sProperties, ApplicationContext applicationContext) {
		this(k8sProperties, applicationContext, defaultKubernetesClientFactory);
	}

	K8sBackend(K8sProperties k8sProperties, ApplicationContext applicationContext, Function<ApplicationContext, KubernetesClient> kubernetesClientFactory) {
		this.k8sProperties = k8sProperties;
		this.kubernetesClient = Lazy.val(() -> kubernetesClientFactory.apply(applicationContext), KubernetesClient.class);
	}

	@Override
	public String getKey() {
		return "k8s";
	}

	@Override
	public Mono<Map<String, Object>> getSecrets() {
		record SecretReference(String namespace, String name) {
		}

		Stream<SecretReference> secretRefs = k8sProperties.secrets().stream()
				.filter(s -> {
					boolean matches = SECRET_REF_PATTERN.matcher(s).matches();
					if ( ! matches) log.atWarn()
							.kv("ref", s)
							.log("Invalid secret reference (expected 'secret-name@namespace')");
					return matches;
				})
				.map(s -> {
					String[] bits = s.split("@", 2);
					return new SecretReference(bits[1], bits[0]);
				});

		return Flux.fromStream(secretRefs)
				.flatMapSequential(ref -> Mono.fromCallable(() -> kubernetesClient
								.secrets()
								.inNamespace(ref.namespace())
								.withName(ref.name())
								.get())
						.flatMap(Mono::justOrEmpty)
						.subscribeOn(Schedulers.boundedElastic()))
				.map(Secret::getData)
				.flatMapSequential(d -> Flux.fromStream(d.entrySet().stream()))
				.collect(MoreCollectors.toSequencedMap(
						Map.Entry::getKey,
						kv -> new String(Base64.getDecoder().decode(kv.getValue()), StandardCharsets.UTF_8),
						rightPrecedence()));
	}
	
	private static KubernetesClient createKubernetesClient(ApplicationContext applicationContext) {
		return new KubernetesClientBuilder()
				.build();
	}
	
	@PreDestroy
	void destroy() {
		kubernetesClient.close();
	}
}
