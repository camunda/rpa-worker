package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.script.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
class ZeebeResourceScriptRepository implements ScriptRepository {

	private final ResourceClient resourceClient;

	@Override
	public String getKey() {
		return "zeebe";
	}

	@Override
	public Mono<RobotScript> findById(String id) {
		return resourceClient.getRpaResource(id)
				.map(rpa -> new RobotScript(rpa.id(), rpa.script()));
	}

	@Override
	public Mono<RobotScript> save(RobotScript robotScript) {
		throw new UnsupportedOperationException("Scripts cannot be deployed via the RPA Worker when using Zeebe as the script source");
	}
}
