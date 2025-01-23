package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.TopologyRequestStep1
import io.camunda.zeebe.client.api.response.Topology
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import java.time.Duration

class ZeebeStartupCheckSpec extends Specification implements PublisherUtils {
	
	ZeebeClient zeebeClient = Stub()
	
	@Subject
	ZeebeStartupCheck check = new ZeebeStartupCheck(zeebeClient)
	
	void "Returns ready event on successful check"() {
		given:
		zeebeClient.newTopologyRequest() >> Stub(TopologyRequestStep1) {
			send() >> new ZeebeClientFutureImpl().tap {
				complete(Stub(Topology))
			}
		}
		
		expect:
		block check.check()
	}

	@Tag("slow") // 3 retries with exponential backoff
	void "Returns error when check is unsuccessful"() {
		given:
		zeebeClient.newTopologyRequest() >> Stub(TopologyRequestStep1) {
			send() >> new ZeebeClientFutureImpl().tap {
				completeExceptionally(new ConnectException("Bang!"))
			}
		}
		
		when:
		check.check().block(Duration.ofSeconds(30))

		then:
		Exception thrown = thrown(IllegalStateException)
		thrown.cause instanceof ConnectException
	}
}
