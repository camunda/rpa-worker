package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.python.AbstractPythonPurgingStartupCheck;
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

	private final RobotExecutionStrategy robotExecutionStrategy;

	public RobotStartupCheck(PythonSetupService pythonSetupService, ApplicationRestarter applicationRestarter, RobotExecutionStrategy robotExecutionStrategy) {
		super(pythonSetupService, applicationRestarter);
		this.robotExecutionStrategy = robotExecutionStrategy;
	}

	@Override
	public Mono<RobotReadyEvent> check() {
		
		if( ! robotExecutionStrategy.shouldCheck())
			return Mono.<RobotReadyEvent>empty()
					.doOnSubscribe(_ -> log.atInfo().log("Robot check skipped because static runtime in use"));
		
		return robotExecutionStrategy.executeRobot(c -> c
						.arg("--version")
						.noFail())

				.flatMap(xr -> xr.exitCode() == RobotService.ROBOT_EXIT_HELP_OR_VERSION_REQUEST && xr.stdout().startsWith("Robot Framework")
						? Mono.just(new RobotReadyEvent())
						: purgeAndRestart()
								.doOnSubscribe(_ -> log.atError()
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
