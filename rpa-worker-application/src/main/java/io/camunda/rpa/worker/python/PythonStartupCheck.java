package io.camunda.rpa.worker.python;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.util.ApplicationRestarter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
class PythonStartupCheck extends AbstractPythonPurgingStartupCheck<PythonReadyEvent> {
	
	private final ProcessService processService;
	private final PythonInterpreter pythonInterpreter;

	public PythonStartupCheck(PythonSetupService pythonSetupService, ApplicationRestarter applicationRestarter, ProcessService processService, PythonInterpreter pythonInterpreter) {
		super(pythonSetupService, applicationRestarter);
		this.processService = processService;
		this.pythonInterpreter = pythonInterpreter;
	}

	@Override
	public Mono<PythonReadyEvent> check() {
		return processService.execute(pythonInterpreter.path(), c -> c.arg("--version").noFail())
				
				.flatMap(xr -> xr.exitCode() == 0 && xr.stdout().startsWith("Python") 
						? Mono.just(new PythonReadyEvent(pythonInterpreter) )
						: purgeAndRestart()
								.doOnSubscribe(_ -> log.atError()
												.kv("pythonInterpreter", pythonInterpreter.path().toAbsolutePath())
												.kv("exitCode", xr.exitCode())
												.kv("stdout", xr.stdout())
												.kv("stderr", xr.stderr())
												.log("Python check failed. The Python environment may be corrupt")))

				.onErrorResume(thrown -> purgeAndRestart()
						.doOnSubscribe(_ -> log.atError()
								.setCause(thrown)
								.log("Python check failed. The Python environment may be corrupt")));
	}

	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_PYTHON;
	}

	void reset() {
		repairAttempted = false;
	}
}
