package io.camunda.rpa.worker.script

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier

class LocalLibraryScriptRepositorySpec extends Specification implements PublisherUtils {

	ScriptProperties scriptProperties = new ScriptProperties(Paths.get("/path/to/scripts/"), "local")
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}

	@Subject
	LocalLibraryScriptRepository repository = new LocalLibraryScriptRepository(scriptProperties, io)
	
	void "Ensures script directory exists on init"() {
		when:
		repository.init()
		
		then:
		1 * io.createDirectories(scriptProperties.path())
	}
	
	void "Returns RobotScript for existing script"() {
		given:
		Path theScript = scriptProperties.path().resolve("real_script.robot")
		
		when:
		RobotScript script = block repository.findById("real_script")

		then:
		1 * io.notExists(theScript) >> false
		1 * io.readString(theScript) >> "the-script-body"
		
		and:
		script.id() == "real_script"
		script.body() == "the-script-body"
	}

	void "Returns empty for non-existing script"() {
		given:
		Path theScript = scriptProperties.path().resolve("fake_script.robot")

		when:
		RobotScript script = block repository.findById("fake_script")

		then:
		1 * io.notExists(theScript) >> true

		and:
		! script
	}
	
	void "Writes deployed scripts into scripts directory"() {
		given:
		RobotScript rs = new RobotScript("the-script", "the-script-body")

		when:
		RobotScript script = block repository.save(rs)
		
		then:
		1 * io.writeString(scriptProperties.path().resolve("the-script.robot"), rs.body())
		
		and:
		script == rs
	}
}
