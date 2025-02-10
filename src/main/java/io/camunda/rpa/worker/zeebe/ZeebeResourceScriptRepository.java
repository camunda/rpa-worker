package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.script.ScriptRepository;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
class ZeebeResourceScriptRepository implements ScriptRepository {
	
	private final ZeebeAuthenticationService zeebeAuthenticationService;
	private final ResourceClient resourceClient;
	private final CamundaClientProperties camundaClientProperties;

	@Override
	public String getKey() {
		return "zeebe";
	}

	@Override
	public Mono<RobotScript> findById(String id) {
		return zeebeAuthenticationService.getAuthToken(camundaClientProperties.getZeebe().getAudience())
				.flatMap(token -> resourceClient.getRpaResource(token, id))
				.map(rpa -> new RobotScript(rpa.id(), rpa.script()));
	}

	@Override
	public Mono<RobotScript> save(RobotScript robotScript) {
		throw new UnsupportedOperationException("Scripts cannot be deployed via the RPA Worker when using Zeebe as the script source");
	}
}
