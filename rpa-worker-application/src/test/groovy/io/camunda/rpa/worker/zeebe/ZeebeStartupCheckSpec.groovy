package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.command.TopologyRequestStep1
import io.camunda.zeebe.client.api.response.Topology
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties
import io.camunda.zeebe.spring.client.properties.common.AuthProperties
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import java.time.Duration

class ZeebeStartupCheckSpec extends Specification implements PublisherUtils {
	
	ZeebeClient zeebeClient = Mock()
	CamundaClientProperties camundaClientProperties = Stub(CamundaClientProperties) {
		getMode() >> CamundaClientProperties.ClientMode.saas
		getAuth() >> Stub(AuthProperties) {
			getClientId() >> "the-client-id"
		}
		getClusterId() >> "the-cluster-id"
		getRegion() >> "the-region"
		getZeebe() >> Stub(ZeebeClientProperties) {
			getGrpcAddress() >> "https://the-grpc-address".toURI()
			getRestAddress() >> "https://the-rest-address".toURI()
			isPreferRestOverGrpc() >> false
			getEnabled() >> true
		}
	}
	
	ZeebeClientStatus zeebeClientStatus = Stub()
	
	@Subject
	ZeebeStartupCheck check = new ZeebeStartupCheck(zeebeClient, camundaClientProperties, zeebeClientStatus)
	
	void "Returns ready event on successful check"() {
		given:
		zeebeClientStatus.isZeebeClientEnabled() >> true
		
		and:
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
		zeebeClientStatus.isZeebeClientEnabled() >> true

		and:
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
	
	void "Skips check when Zeebe is not enabled"() {
		given:
		zeebeClientStatus.isZeebeClientEnabled() >> false

		when:
		ZeebeReadyEvent r = block check.check()
		
		then:
		! r
		
		and:
		0 * zeebeClient._(*_)
	}
}
