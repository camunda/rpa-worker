package io.camunda.rpa.worker.zeebe

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.ZeebeFuture
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1
import io.camunda.zeebe.client.api.command.FinalCommandStep
import io.camunda.zeebe.client.api.response.ActivateJobsResponse
import io.camunda.zeebe.client.api.response.ActivatedJob
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.CountDownLatch
import java.util.function.BiFunction

class ZeebeJobPollerSpec extends Specification {
	
	static final String TASK_PREFIX = "camunda::RPA-Task::"

	ActivateJobsCommandStep1 activate1 = Mock()

	ZeebeProperties zeebeProperties = ZeebeProperties.builder()
			.rpaTaskPrefix(TASK_PREFIX)
			.workerTags(["tag-one", "tag-two"].toSet())
			.authEndpoint("http://auth/".toURI())
			.maxConcurrentJobs(1)
			.build()
	
	ZeebeClient zeebeClient = Stub() {
		newActivateJobsCommand() >> { activate1 }
	}
	ZeebeJobService zeebeJobService = Mock()
	
	List<String> pollQueue
	Iterable<String> tagGenerator = Stub() {
		iterator() >> { pollQueue.collect { TASK_PREFIX + it }.iterator() }
		spliterator() >> { pollQueue.collect { TASK_PREFIX + it }.spliterator() }
	}

	@Subject
	ZeebeJobPoller jobPoller = new ZeebeJobPoller(zeebeProperties, zeebeClient, zeebeJobService, tagGenerator)
	
	void "Starts polling all tags on init"() {
		given:
		pollQueue = ["tag-one", "tag-two", "tag-one"]
		
		when:
		jobPoller.init()

		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-one") >> activateJobClientCall()

		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-two") >> activateJobClientCall()

		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-one") >> activateJobClientCall()
		
		then:
		0 * activate1._
	}
	
	void "Passes job to be ran when received"() {
		given:
		ActivatedJob job = Stub()
		
		and:
		pollQueue = ["tag-one"]
		activate1.jobType(TASK_PREFIX + "tag-one") >> activateJobClientCall(job)

		when:
		jobPoller.init()
		
		then:
		1 * zeebeJobService.handleJob(job) >> Mono.empty()
	}

	void "Polls up to job limit"() {
		given:
		ActivatedJob job1 = Stub()
		ActivatedJob job2 = Stub()
		CountDownLatch job1Latch = new CountDownLatch(1)
		CountDownLatch job2Latch = new CountDownLatch(1)

		and:
		pollQueue = ["tag-one", "tag-two"]
		activate1.jobType(TASK_PREFIX + "tag-one") >> activateJobClientCall(job1)
		activate1.jobType(TASK_PREFIX + "tag-two") >> activateJobClientCall(job2)

		when:
		jobPoller.init()

		then:
		1 * zeebeJobService.handleJob(job1) >> Mono.fromRunnable { job1Latch.await() }.subscribeOn(Schedulers.boundedElastic())
		0 * zeebeJobService.handleJob(job2) 
		
		when:
		job1Latch.countDown()
		job2Latch.await()
		
		then:
		1 * zeebeJobService.handleJob(job2) >> {
			job2Latch.countDown()
			return Mono.empty() 
		}
	}

	void "Runs concurrent jobs"() {
		given:
		ActivatedJob job1 = Stub()
		ActivatedJob job2 = Stub()
		ActivatedJob job3 = Stub()
		CountDownLatch job1Latch = new CountDownLatch(1)
		CountDownLatch job2Latch = new CountDownLatch(1)
		CountDownLatch job3Latch = new CountDownLatch(1)

		and:
		pollQueue = ["tag-one", "tag-two", "tag-one"]
		activate1.jobType(TASK_PREFIX + "tag-one") >>> [activateJobClientCall(job1), activateJobClientCall(job3)] >> activateJobClientCall()
		activate1.jobType(TASK_PREFIX + "tag-two") >> activateJobClientCall(job2)

		and:
		@Subject
		ZeebeJobPoller concurrentPoller = new ZeebeJobPoller(
				zeebeProperties.toBuilder().maxConcurrentJobs(2).build(),
				zeebeClient,
				zeebeJobService)

		when:
		concurrentPoller.init()

		then:
		1 * zeebeJobService.handleJob(job1) >> Mono.fromRunnable { job1Latch.await() }.subscribeOn(Schedulers.boundedElastic())
		1 * zeebeJobService.handleJob(job2) >> Mono.fromRunnable { job2Latch.await() }.subscribeOn(Schedulers.boundedElastic())
		0 * zeebeJobService.handleJob(job3)

		when:
		job1Latch.countDown()
		job2Latch.countDown()
		job3Latch.await()

		then:
		1 * zeebeJobService.handleJob(job3) >> {
			job3Latch.countDown()
			return Mono.empty()
		}
	}

	void "Ignores errors and continues polling"() {
		given:
		pollQueue = ["tag-one", "tag-two", "tag-one"]
		ActivatedJob job = Stub()

		when:
		jobPoller.init()

		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-one") >> { throw new RuntimeException("Error in client!") }

		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-two") >> activateJobClientCall(job)
		1 * zeebeJobService.handleJob(job) >> { Mono.error(new RuntimeException("Error in handler!")) }

		then:
		1 * activate1.jobType(TASK_PREFIX + "tag-one") >> activateJobClientCall()
	}

	private ActivateJobsCommandStep1.ActivateJobsCommandStep2 activateJobClientCall(ActivatedJob job = null) {
		FinalCommandStep activateFinal = Stub() {
			send() >> Stub(ZeebeFuture) {
				handle(_) >> { BiFunction fn ->
					Mono.just({ -> job ? [job] : [] } as ActivateJobsResponse)
							.toFuture()
							.handle(fn)
				}
			}
		}

		ActivateJobsCommandStep1.ActivateJobsCommandStep3 activate3 = Stub() {
			requestTimeout(ZeebeJobPoller.JOB_POLL_TIME) >> activateFinal
		}

		return Stub(ActivateJobsCommandStep1.ActivateJobsCommandStep2) {
			maxJobsToActivate(1) >> activate3
		}
	}
}
