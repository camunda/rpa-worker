package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.AbstractPythonPurgingStartupCheck;
import io.camunda.rpa.worker.python.PythonInterpreter;
import io.camunda.rpa.worker.python.PythonSetupService;
import io.camunda.rpa.worker.util.ApplicationRestarter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Order
class RobotStartupCheck extends AbstractPythonPurgingStartupCheck<RobotReadyEvent> {
	
	private final ProcessService processService;
	private final PythonInterpreter pythonInterpreter;

	public RobotStartupCheck(PythonSetupService pythonSetupService, ApplicationRestarter applicationRestarter, ProcessService processService, PythonInterpreter pythonInterpreter) {
		super(pythonSetupService, applicationRestarter);
		this.processService = processService;
		this.pythonInterpreter = pythonInterpreter;
	}

	@Override
	public Mono<RobotReadyEvent> check() {
		return processService.execute(pythonInterpreter.path(), c -> c
						.arg("-m").arg("robot")
						.arg("--version")
						.noFail())

				.flatMap(xr -> xr.exitCode() == RobotService.ROBOT_EXIT_HELP_OR_VERSION_REQUEST && xr.stdout().startsWith("Robot Framework")
						? Mono.just(new RobotReadyEvent())
						: purgeAndRestart()
								.doOnSubscribe(_ -> log.atError()
										.kv("pythonInterpreter", pythonInterpreter.path().toAbsolutePath())
										.kv("exitCode", xr.exitCode())
										.kv("stdout", xr.stdout())
										.kv("stderr", xr.stderr())
										.log("Robot check failed. The Python environment may be corrupt")))

				.onErrorResume(thrown -> purgeAndRestart()
						.doOnSubscribe(_ -> log.atError()
								.setCause(thrown)
								.log("Robot check failed. The Python environment may be corrupt")));
	}
	
	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_ROBOT;
	}
	
	void reset() {
		repairAttempted = false;
	}
}
