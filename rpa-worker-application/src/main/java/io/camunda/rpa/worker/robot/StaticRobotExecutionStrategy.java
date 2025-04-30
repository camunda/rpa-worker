package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.vavr.control.Try;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

		if ( ! staticRuntimeResource.exists())
			throw new IllegalStateException("The static runtime is not supported in this distribution");

		runtimeDir = io.createTempDirectory("rpa-worker-runtime");
		PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

		io.run(() -> Flux.fromArray(Try.of(() -> resourceResolver.getResources("runtime/**")).get())
						.filter(Resource::isReadable)
						.flatMap(r -> Flux.using(r::getInputStream, Flux::just)
								.doOnNext(in -> {
									Path target = runtimeDir.resolve(getPath(r));
									io.createDirectories(target.getParent());
									io.copy(in, target);
								}))
						.then()
						.doOnSuccess(_ -> Try.of(() -> io.setPosixFilePermissions(
								runtimeDir.resolve("runtime/").resolve(robotExeName),
								EnumSet.allOf(PosixFilePermission.class))))
						.block())
				.block();
	}

	private String getPath(Resource r) {
		String p = switch (r) {
			case ClassPathResource cpr -> cpr.getPath();
			case FileSystemResource fsr -> fsr.getPath();
			default -> throw new IllegalStateException(r.getClass().getName());
		};
		
		if(p.startsWith("/")) p = p.substring(1);
		return p;
	}

	@Override
	public Mono<ProcessService.ExecutionResult> executeRobot(UnaryOperator<ExecutionCustomizer> customizer) {
		return processService.execute(
				runtimeDir.resolve("runtime/").resolve(robotExeName),
				customizer);
	}

	@Override
	public boolean shouldCheck() {
		return false;
	}
}
