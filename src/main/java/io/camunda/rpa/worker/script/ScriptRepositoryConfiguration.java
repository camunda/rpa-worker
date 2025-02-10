package io.camunda.rpa.worker.script;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
class ScriptRepositoryConfiguration {
	
	private final ObjectProvider<ScriptRepository> scriptRepositories;
	private final ScriptProperties scriptProperties;

	///  See [this section in the Spring Native documentation](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html#packaging.native-image.introducing-graalvm-native-images.understanding-aot-processing)
	/// for why this is required
	@Bean
	public ConfiguredScriptRepository scriptRepository() {
		ScriptRepository scriptRepository = scriptRepositories.stream()
				.filter(it -> it.getKey().equals(scriptProperties.source()))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("Script repository with key %s not found".formatted(scriptProperties.source())));
		
		log.atInfo()
				.kv("repository", scriptRepository.getClass().getName())
				.log("Using repository for scripts");

		ProxyFactory factory = new ProxyFactory(scriptRepository);
		factory.addInterface(ConfiguredScriptRepository.class);
		return (ConfiguredScriptRepository) factory.getProxy();
	}
}
