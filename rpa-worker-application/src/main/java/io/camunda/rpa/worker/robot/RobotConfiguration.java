package io.camunda.rpa.worker.robot;

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
}
