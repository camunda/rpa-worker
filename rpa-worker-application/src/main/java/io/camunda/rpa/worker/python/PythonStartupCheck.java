package io.camunda.rpa.worker.python;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.check.StartupCheck;
import io.camunda.rpa.worker.pexec.ProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
class PythonStartupCheck implements StartupCheck<PythonReadyEvent> {
	
	private final ProcessService processService;
	private final PythonInterpreter pythonInterpreter;

	@Override
	public Mono<PythonReadyEvent> check() {
		return processService.execute(pythonInterpreter.path(), c -> c.arg("--version").noFail())
				
				.doOnError(thrown -> log.atError()
						.kv("pythonInterpreter", pythonInterpreter.path().toAbsolutePath())
						.setCause(thrown)
						.log("Python check failed"))
				
				.flatMap(xr -> xr.exitCode() == 0 && xr.stdout().startsWith("Python") 
						? Mono.just(new PythonReadyEvent(pythonInterpreter) )
						: Mono.<PythonReadyEvent>error(new RuntimeException())
								.doOnSubscribe(_ -> log.atError()
												.kv("pythonInterpreter", pythonInterpreter.path().toAbsolutePath())
												.kv("exitCode", xr.exitCode())
												.kv("stdout", xr.stdout())
												.kv("stderr", xr.stderr())
												.log("Python check failed")));
	}

	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_PYTHON;
	}
}
