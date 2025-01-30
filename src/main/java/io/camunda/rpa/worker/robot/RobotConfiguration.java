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
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@RequiredArgsConstructor
class RobotConfiguration {
	
	private final RobotProperties properties;
	
	@Bean
	public Scheduler robotWorkScheduler() {
		AtomicInteger threadNum = new AtomicInteger(1);
		return Schedulers.fromExecutorService(Executors.newFixedThreadPool(properties.maxConcurrentJobs(), r -> {
			Thread thread = Executors.defaultThreadFactory().newThread(r);
			thread.setName("robot-%s".formatted(threadNum.getAndIncrement()));
			thread.setDaemon(true);
			return thread;
		}), "robot");
	}

	@Bean
	public YamlMapper yamlMapper(ObjectMapper objectMapper) {
		return new YamlMapper(objectMapper.copyWith(new YAMLFactory()));
	}
}
