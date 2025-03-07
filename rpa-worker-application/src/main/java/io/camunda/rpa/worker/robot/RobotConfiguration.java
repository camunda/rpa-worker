package io.camunda.rpa.worker.robot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.rpa.worker.util.YamlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE;
import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE;

@Configuration
@RequiredArgsConstructor
class RobotConfiguration {
	
	private final RobotProperties properties;
	
	@Bean
	public Scheduler robotWorkScheduler() {
		return Schedulers.newBoundedElastic(
				DEFAULT_BOUNDED_ELASTIC_SIZE,
				DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
				"robot",
				60,
				true);
	}

	@Bean
	public YamlMapper yamlMapper(ObjectMapper objectMapper) {
		return new YamlMapper(objectMapper.copyWith(new YAMLFactory()));
	}
}
