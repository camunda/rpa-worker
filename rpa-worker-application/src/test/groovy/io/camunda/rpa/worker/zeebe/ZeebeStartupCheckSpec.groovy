package io.camunda.rpa.worker.zeebe

import io.camunda.client.CamundaClient
import io.camunda.client.api.command.TopologyRequestStep1
import io.camunda.client.api.response.Topology
import io.camunda.client.impl.CamundaClientFutureImpl
import io.camunda.client.spring.properties.CamundaClientAuthProperties
import io.camunda.client.spring.properties.CamundaClientCloudProperties
import io.camunda.client.spring.properties.CamundaClientProperties
import io.camunda.rpa.worker.PublisherUtils
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import java.time.Duration

class ZeebeStartupCheckSpec extends Specification implements PublisherUtils {
	
	CamundaClient zeebeClient = Mock()
	ObjectProvider<CamundaClient> zeebeClientProvider = Stub() {
		getObject() >> zeebeClient
	}

	CamundaClientProperties camundaClientProperties = Stub(CamundaClientProperties) {
		getMode() >> CamundaClientProperties.ClientMode.saas
		getAuth() >> Stub(CamundaClientAuthProperties) {
			getClientId() >> "the-client-id"
		}
		getCloud() >> Stub(CamundaClientCloudProperties) {
			getClusterId() >> "the-cluster-id"
			getRegion() >> "the-region"
		}
		getGrpcAddress() >> "https://the-grpc-address".toURI()
		getRestAddress() >> "https://the-rest-address".toURI()
		getPreferRestOverGrpc() >> false
		getEnabled() >> true
	}
	
	ObjectProvider<CamundaClientProperties> clientPropertiesProvider = Stub() {
		getObject() >> camundaClientProperties
	}

	ZeebeClientStatus zeebeClientStatus = Stub()
	
	@Subject
	ZeebeStartupCheck check = new ZeebeStartupCheck(zeebeClientProvider, clientPropertiesProvider, zeebeClientStatus)
	
	void "Returns ready event on successful check"() {
		given:
		zeebeClientStatus.isZeebeClientEnabled() >> true
		
		and:
		zeebeClient.newTopologyRequest() >> Stub(TopologyRequestStep1) {
			send() >> new CamundaClientFutureImpl().tap {
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
			send() >> new CamundaClientFutureImpl().tap {
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
