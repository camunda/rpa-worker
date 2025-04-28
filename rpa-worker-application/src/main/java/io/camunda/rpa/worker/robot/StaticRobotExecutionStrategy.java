package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.vavr.control.Try;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedInputStream;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.function.UnaryOperator;

class StaticRobotExecutionStrategy implements RobotExecutionStrategy {
	
	private static final String robotExeName = System.getProperty("os.name").contains("Windows")
			? "robot.exe"
			: "robot";

	private final ProcessService processService;
	private final Path runtimeDir;

	public StaticRobotExecutionStrategy(ProcessService processService, IO io) {
		this.processService = processService;

		ClassPathResource staticRuntimeResource = new ClassPathResource("runtime/%s".formatted(robotExeName));
		if( ! staticRuntimeResource.exists())
			throw new IllegalStateException("The static runtime is not supported in this distribution");

		runtimeDir = io.createTempDirectory("rpa-worker-runtime");
		io.run(() -> Flux.using(staticRuntimeResource::getInputStream,
								is -> Mono.just(new BufferedInputStream(is)))
						.doOnNext(is -> io.copy(is, runtimeDir.resolve(robotExeName)))
						.doOnNext(_ -> Try.run(() -> io.setPosixFilePermissions(runtimeDir.resolve(robotExeName), EnumSet.allOf(PosixFilePermission.class))))
						.single()
						.block())
				.block();
	}
	
	@Override
	public Mono<ProcessService.ExecutionResult> executeRobot(UnaryOperator<ExecutionCustomizer> customizer) {
		return processService.execute(
				runtimeDir.resolve(robotExeName),
				customizer);
	}

	@Override
	public boolean shouldCheck() {
		return false;
	}
}
