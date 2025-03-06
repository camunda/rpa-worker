package io.camunda.rpa.worker.zeebe


import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.util.concurrent.TimeUnit

class ZeebeMetricsServiceSpec extends Specification {
	
	MeterRegistry meterRegistry = new SimpleMeterRegistry()
	ZeebeProperties zeebeProperties = ZeebeProperties.builder()
			.maxConcurrentJobs(2)
			.build()
	
	@Subject
	ZeebeMetricsService service = new ZeebeMetricsService(meterRegistry, zeebeProperties).init()
	
	void "Records maximum job capacity"() {
		expect:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.capacity.total").gauge().value() == 2
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.capacity.available").gauge().value() == 2
	}
	
	void "Records job activation"() {
		when:
		service.onZeebeJobReceived("job-type")
		
		then:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.active").gauge().value() == 1
		
		and:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.capacity.available").gauge().value() == 1
	}
	
	void "Records successful job completion"() {
		given:
		service.onZeebeJobReceived("job-type")
		
		when:
		service.onZeebeJobSuccess("job-type", Duration.ofSeconds(3))
		
		then:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.active").gauge().value() == 0
		
		and:
		with(meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.success").counter()) {
			measure().first().value == 1
			id.getTag("type") == "job-type"
		}
		
		and:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.capacity.available").gauge().value() == 2
		
		and:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.runtime").timer().mean(TimeUnit.SECONDS) == 3
	}

	void "Records job failure"(String code) {
		given:
		service.onZeebeJobReceived("job-type")

		when:
		service.onZeebeJobFail("job-type", code)

		then:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.active").gauge().value() == 0

		and:
		with(meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.failure")
				.tag("code", code).counter()) {
			
			measure().first().value == 1
			id.getTag("type") == "job-type"
		}

		and:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.capacity.available").gauge().value() == 2
		
		where:
		code << ["ROBOT_TASKFAIL", "ROBOT_ERROR", "ROBOT_TIMEOUT"]
	}

	void "Records job error"() {
		given:
		service.onZeebeJobReceived("job-type")

		when:
		service.onZeebeJobError("job-type")

		then:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.active").gauge().value() == 0

		and:
		with(meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.error").counter()) {
			measure().first().value == 1
			id.getTag("type") == "job-type"
		}

		and:
		meterRegistry.get(ZeebeMetricsService.METRIC_PREFIX + "job.capacity.available").gauge().value() == 2
	}
}
