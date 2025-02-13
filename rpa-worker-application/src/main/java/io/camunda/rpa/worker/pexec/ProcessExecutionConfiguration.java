package io.camunda.rpa.worker.pexec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE;
import static reactor.core.scheduler.Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE;

@Configuration
class ProcessExecutionConfiguration {
	@Bean
	public Scheduler processExecutionScheduler() {
		return Schedulers.newBoundedElastic(
				DEFAULT_BOUNDED_ELASTIC_SIZE,
				DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
				"pexec",
				60,
				true);
	}
}
