package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.ExistingEnvironmentProvider;
import io.camunda.rpa.worker.python.PythonRuntimeProperties;
import io.camunda.rpa.worker.python.PythonSetupService;
import io.camunda.rpa.worker.python.SystemPythonProvider;
import io.camunda.rpa.worker.util.InternetConnectivityProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
class RobotExecutionStrategyFactoryBean implements FactoryBean<RobotExecutionStrategy> {
	
	private final PythonRuntimeProperties pythonRuntimeProperties;
	private final ProcessService processService;
	private final ExistingEnvironmentProvider existingEnvironmentProvider;
	private final SystemPythonProvider systemPythonProvider;
	private final InternetConnectivityProvider internetConnectivityProvider;
	private final IO io;
	private final PythonSetupService pythonSetupService;
	
	@Override
	public Class<?> getObjectType() {
		return RobotExecutionStrategy.class;
	}

	@Override
	public RobotExecutionStrategy getObject() throws Exception {
		return (switch(pythonRuntimeProperties.type()) {
			case Auto -> detectStrategy();
			case Python -> pythonStrategy();
			case Static -> staticStrategy();
		}).block();
	}

	private Mono<RobotExecutionStrategy> detectStrategy() {
		return Mono.justOrEmpty(existingEnvironmentProvider.existingPythonEnvironment())
				.doOnNext(_ -> log.atInfo()
						.log("Found existing Python environment, will use Python execution strategy"))
				.flatMap(_ -> pythonStrategy())

				.switchIfEmpty(Mono.defer(systemPythonProvider::systemPython)
						.flatMap(_ -> internetConnectivityProvider.hasConnectivity())
						.filter(it -> it)
						.doOnNext(_ -> log.atInfo()
								.log("Found supported system Python and internet connectivity, will use Python execution strategy"))
						.flatMap(_ -> pythonStrategy()))

				.switchIfEmpty(Mono.defer(() -> Mono.just(System.getProperty("os.name").contains("Windows")))
						.filter(it -> it)
						.flatMap(_ -> internetConnectivityProvider.hasConnectivity())
						.filter(it -> it)
						.doOnNext(_ -> log.atInfo()
								.log("Found Python provision-capable platform and internet connectivity, will use Python execution strategy"))
						.flatMap(_ -> pythonStrategy()))

				.switchIfEmpty(Mono.defer(() -> staticStrategy()
						.doOnNext(_ -> log.atInfo()
								.log("Python strategy not supported (no supported Python or no internet connectivity), will use Static execution strategy"))));
	}

	private Mono<RobotExecutionStrategy> pythonStrategy() {
		return pythonSetupService.getPythonInterpreter()
				.map(interp -> new PythonRobotExecutionStrategy(processService, interp));
	}

	private Mono<RobotExecutionStrategy> staticStrategy() {
		return Mono.just(new StaticRobotExecutionStrategy(processService, io));
	}
}
