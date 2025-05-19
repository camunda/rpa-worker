package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.script.RobotScript
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths

class ZeebeResourceScriptRepositorySpec extends Specification implements PublisherUtils {

	ResourceClient resourceClient = Mock()
	
	@Subject
	ZeebeResourceScriptRepository repository = 
			new ZeebeResourceScriptRepository(resourceClient)
	
	void "Fetches script resource from Zeebe"() {
		given:
		resourceClient.getRpaResource("the-resource-key") >> Mono.just(RpaResource.builder()
						.id("the-resource-key")
						.name("the-resource-name")
						.executionPlatform("")
						.executionPlatformVersion("")
						.script("the-script-body")
						.build())
		
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
				new RpaResource("the-resource-key", "the-resource-name", "", "", "the-script-body", [:]))
		
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
		
		when:
		RobotScript script2 = block repository.getById("the-resource-key")

		then:
		0 * resourceClient.getRpaResource(_)

		script2.id() == "the-resource-key"
		script2.body() == "the-script-body"
	}

	void "Fetches script resource from Zeebe - includes additional files"() {
		given:
		resourceClient.getRpaResource("the-resource-key") >> Mono.just(RpaResource.builder()
				.id("the-resource-key")
				.name("the-resource-name")
				.executionPlatform("")
				.executionPlatformVersion("")
				.script("the-script-body")
				.file('one.resource', 'b25lLnJlc291cmNlIGNvbnRlbnQ=')
				.file('two/three.resource', 'dGhyZWUucmVzb3VyY2UgY29udGVudA==')
				.build())

		when:
		RobotScript script = block repository.getById("the-resource-key")

		then:
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
		script.files() == [
				(Paths.get("one.resource"))    : "one.resource content".bytes,
				(Paths.get("two/three.resource")): "three.resource content".bytes
		]
	}
}
