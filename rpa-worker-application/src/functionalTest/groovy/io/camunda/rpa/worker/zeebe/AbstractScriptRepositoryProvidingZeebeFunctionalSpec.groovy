package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.script.ScriptRepository
import org.spockframework.spring.SpringBean
import reactor.core.publisher.Mono

abstract class AbstractScriptRepositoryProvidingZeebeFunctionalSpec extends AbstractZeebeFunctionalSpec {

	static Map<String, String> scriptContent

	@SpringBean
	ScriptRepository scriptRepository = new ScriptRepository() {
		@Override
		String getKey() {
			return "stub"
		}

		@Override
		Mono<RobotScript> findById(String id) {
			if (scriptContent.containsKey(id))
				return Mono.just(new RobotScript(id, scriptContent[id]))
			return Mono.empty()
		}
	}

	abstract Map<String, String> getScripts()

	void setupSpec() {
		scriptContent = scripts
	}
}
