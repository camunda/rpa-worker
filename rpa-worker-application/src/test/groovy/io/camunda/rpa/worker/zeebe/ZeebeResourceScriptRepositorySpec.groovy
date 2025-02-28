package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.script.RobotScript
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class ZeebeResourceScriptRepositorySpec extends Specification implements PublisherUtils {

	ResourceClient resourceClient = Mock()
	
	@Subject
	ZeebeResourceScriptRepository repository = 
			new ZeebeResourceScriptRepository(resourceClient)
	
	void "Fetches script resource from Zeebe"() {
		given:
		resourceClient.getRpaResource("the-resource-key") >> Mono.just(
				new RpaResource("the-resource-key", "the-resource-name", "", "", "the-script-body"))
		
		when:
		RobotScript script = block repository.getById("the-resource-key")
		
		then:
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
	}

	void "Caches script and uses cache when available"() {
		given:

		when:
		RobotScript script = block repository.getById("the-resource-key")

		then:
		1 * resourceClient.getRpaResource("the-resource-key") >> Mono.just(
				new RpaResource("the-resource-key", "the-resource-name", "", "", "the-script-body"))
		
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
		
		when:
		RobotScript script2 = block repository.getById("the-resource-key")

		then:
		0 * resourceClient.getRpaResource(_)

		script2.id() == "the-resource-key"
		script2.body() == "the-script-body"
	}
}
