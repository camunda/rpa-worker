package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.camunda.rpa.worker.check.StartupCheck;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.PythonInterpreter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
@Order
class RobotStartupCheck implements StartupCheck<RobotReadyEvent> {
	
	private final ProcessService processService;
	private final PythonInterpreter pythonInterpreter;

	@Override
	public Mono<RobotReadyEvent> check() {
		return processService.execute(pythonInterpreter.path(), c -> c
						.arg("-m").arg("robot")
						.arg("--version")
						.noFail())
				
				.doOnError(thrown -> log.atError()
						.kv("pythonInterpreter", pythonInterpreter.path().toAbsolutePath())
						.setCause(thrown)
						.log("Robot check failed"))

				.flatMap(xr -> xr.exitCode() == RobotService.ROBOT_EXIT_HELP_OR_VERSION_REQUEST && xr.stdout().startsWith("Robot Framework") 
						? Mono.just(new RobotReadyEvent())
						: Mono.<RobotReadyEvent>error(new RuntimeException())
								.doOnSubscribe(_ -> log.atError()
												.kv("pythonInterpreter", pythonInterpreter.path().toAbsolutePath())
												.kv("exitCode", xr.exitCode())
												.kv("stdout", xr.stdout())
												.kv("stderr", xr.stderr())
												.log("Robot check failed")));
	}

	@Override
	public int getExitCodeForFailure() {
		return RpaWorkerApplication.EXIT_NO_ROBOT;
	}
}
