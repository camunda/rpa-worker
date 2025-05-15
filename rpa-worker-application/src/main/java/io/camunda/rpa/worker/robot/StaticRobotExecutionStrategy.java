package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ExecutionCustomizer;
import io.camunda.rpa.worker.pexec.ProcessService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

import static io.camunda.rpa.worker.util.ArchiveUtils.extractArchive;

class StaticRobotExecutionStrategy implements RobotExecutionStrategy {
	
	private static final String robotExeName = System.getProperty("os.name").contains("Windows")
			? "robot.exe"
			: "robot";

	private final ProcessService processService;
	private final Path runtimeDir;

	public StaticRobotExecutionStrategy(ProcessService processService, IO io) {
		this.processService = processService;

		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource staticRuntimeZipResource = resolver.getResource("runtime.zip");

		if ( ! staticRuntimeZipResource.exists())
			throw new IllegalStateException("The static runtime is not supported in this distribution");

		runtimeDir = io.createTempDirectory("rpa-worker-runtime");
		Path staticRuntimeZip = runtimeDir.resolve("runtime.zip");
		io.run(() -> Flux.using(staticRuntimeZipResource::getInputStream, Flux::just)
						.doOnNext(in -> io.copy(in, staticRuntimeZip))
						.doOnNext(_ -> extractArchive(io, staticRuntimeZip, runtimeDir))
						.then()
						.block())
				.block();
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
