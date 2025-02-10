package io.camunda.rpa.worker.script.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.script.ConfiguredScriptRepository
import io.camunda.rpa.worker.script.RobotScript
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

class LocalLibraryControllerSpec extends Specification implements PublisherUtils {
	
	ConfiguredScriptRepository scriptRepository = Mock()
	
	@Subject
	LocalLibraryController controller = new LocalLibraryController(scriptRepository)
	
	void "Passes script to repository to be saved"() {
		when:
		ResponseEntity<?> r = block controller.deployScript(new DeployScriptRequest("script-id", "script-body"))
		
		then:
		1 * scriptRepository.save(new RobotScript("script-id", "script-body")) >> Mono.empty()
		
		and:
		r.statusCode == HttpStatus.NO_CONTENT
	}
}
