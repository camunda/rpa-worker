package io.camunda.rpa.worker.zeebe;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
class ZeebeMetricsService {
	
	static final String METRIC_PREFIX = "rpaworker.zeebe.";
	
	private final MeterRegistry meterRegistry;
	private final ZeebeProperties zeebeProperties;
	
	private final Map<String, Counter> jobTypeCounters = new ConcurrentHashMap<>();
	private final Map<String, Timer> jobTypeTimers = new ConcurrentHashMap<>();

	private final AtomicInteger zeebeJobsActive = new AtomicInteger();
	
	@PostConstruct
	ZeebeMetricsService init() {
		Gauge.builder(METRIC_PREFIX + "job.active", zeebeJobsActive::get)
				.register(meterRegistry);

		Gauge.builder(METRIC_PREFIX + "job.capacity.total", zeebeProperties::maxConcurrentJobs)
				.register(meterRegistry);

		Gauge.builder(METRIC_PREFIX + "job.capacity.available",
						() -> zeebeProperties.maxConcurrentJobs() - zeebeJobsActive.get())
				.register(meterRegistry);
		
		return this;
	}

	public void onZeebeJobReceived(String type) {
		zeebeJobsActive.incrementAndGet();
		
		jobTypeCounters.computeIfAbsent(type,
						t -> Counter.builder(METRIC_PREFIX + "job.received.total")
								.tag("type", t)
								.register(meterRegistry))
				.increment();

		jobTypeTimers.computeIfAbsent(type,
				t -> Timer.builder(METRIC_PREFIX + "job.runtime")
						.publishPercentiles(0.5, 0.95)
						.tag("type", t)
						.register(meterRegistry));

		jobTypeCounters.computeIfAbsent(type + "/result/SUCCESS",
				_ -> Counter.builder(METRIC_PREFIX + "job.success")
						.tag("type", type)
						.register(meterRegistry));

		jobTypeCounters.computeIfAbsent(type + "/result/ROBOT_TASKFAIL",
				_ -> Counter.builder(METRIC_PREFIX + "job.failure")
						.tag("type", type)
						.tag("code", "ROBOT_TASKFAIL")
						.register(meterRegistry));
		
		jobTypeCounters.computeIfAbsent(type + "/result/ROBOT_TIMEOUT",
				_ -> Counter.builder(METRIC_PREFIX + "job.failure")
						.tag("type", type)
						.tag("code", "ROBOT_TIMEOUT")
						.register(meterRegistry));
		
		jobTypeCounters.computeIfAbsent(type + "/result/ROBOT_ERROR",
				_ -> Counter.builder(METRIC_PREFIX + "job.failure")
						.tag("type", type)
						.tag("code", "ROBOT_ERROR")
						.register(meterRegistry));

		jobTypeCounters.computeIfAbsent(type + "/result/ERROR",
				_ -> Counter.builder(METRIC_PREFIX + "job.error")
						.tag("type", type)
						.register(meterRegistry));
	}
	
	public void onZeebeJobSuccess(String type, Duration duration) {
		zeebeJobsActive.decrementAndGet();
		jobTypeTimers.get(type).record(duration);
		jobTypeCounters.get(type + "/result/SUCCESS").increment();
	}

	public void onZeebeJobFail(String type, String code) {
		zeebeJobsActive.decrementAndGet();
		jobTypeCounters.get(type + "/result/%s".formatted(code)).increment();
	}
	
	public void onZeebeJobError(String type) {
		zeebeJobsActive.decrementAndGet();
		jobTypeCounters.get(type + "/result/ERROR").increment();
	}
}
