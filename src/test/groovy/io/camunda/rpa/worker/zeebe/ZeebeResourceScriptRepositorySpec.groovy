package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties
import io.camunda.zeebe.spring.client.properties.common.ZeebeClientProperties
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ZeebeResourceScriptRepositorySpec extends Specification implements PublisherUtils {
	
	CamundaClientProperties camundaClientProperties = Stub() {
		getZeebe() >> Stub(ZeebeClientProperties) {
			getAudience() >> "zeebe.audience"
		}
	}

	ZeebeAuthenticationService zeebeAuthenticationService = Stub() {
		getAuthToken("zeebe.audience") >> Mono.just("the-auth-token")
	}

	ResourceClient resourceClient = Stub()
	
	@Subject
	ZeebeResourceScriptRepository repository = 
			new ZeebeResourceScriptRepository(zeebeAuthenticationService, resourceClient, camundaClientProperties)
	
	void "Fetches script resource from Zeebe"() {
		given:
		resourceClient.getRpaResource("the-auth-token", "the-resource-key") >> Mono.just(
				new RpaResource("the-resource-key", "the-resource-name", "", "", "the-script-body"))
		
		when:
		RobotScript script = block repository.getById("the-resource-key")
		
		then:
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
	}
}
