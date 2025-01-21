package io.camunda.rpa.worker.robot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.rpa.worker.util.YamlMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executors;

@Configuration
@RequiredArgsConstructor
class RobotConfiguration {
	
	private final RobotProperties properties;
	
	@Bean
	public Scheduler robotWorkScheduler() {
		return Schedulers.fromExecutorService(Executors.newFixedThreadPool(properties.maxConcurrentJobs()), "robot");
	}

	@Bean
	public YamlMapper yamlMapper(ObjectMapper objectMapper) {
		return new YamlMapper(objectMapper.copyWith(new YAMLFactory()));
	}
}
