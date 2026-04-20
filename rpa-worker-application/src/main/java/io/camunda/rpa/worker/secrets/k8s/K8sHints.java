package io.camunda.rpa.worker.secrets.k8s;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.stream.Stream;

@Configuration
@ImportRuntimeHints(K8sHints.class)
class K8sHints implements RuntimeHintsRegistrar {
	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		Stream.<Class<?>>of()
				.forEach(klass -> hints
						.reflection()
						.registerType(
								klass,
								MemberCategory.values()));
	}
}
