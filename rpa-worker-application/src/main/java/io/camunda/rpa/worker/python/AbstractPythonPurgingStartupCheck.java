package io.camunda.rpa.worker.python;

import io.camunda.rpa.worker.check.StartupCheck;
import io.camunda.rpa.worker.util.ApplicationRestarter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Slf4j
public abstract class AbstractPythonPurgingStartupCheck<T extends ApplicationEvent> implements StartupCheck<T> {
	
	private final PythonSetupService pythonSetupService;
	private final ApplicationRestarter applicationRestarter;
	
	protected static volatile boolean repairAttempted = false;
	
	public Mono<T> purgeAndRestart() {
		if(repairAttempted)
			return Mono.error(new RuntimeException("The attempt to repair the Python environment failed"));
		
		repairAttempted = true;
		
		return pythonSetupService.purgeEnvironment()
				.doOnSubscribe(_ -> log.atInfo().log("The Python environment is going to be rebuilt"))
				.then(Mono.defer(() -> Mono.fromRunnable(applicationRestarter::restart)))
				.then(Mono.never());
	}
}
