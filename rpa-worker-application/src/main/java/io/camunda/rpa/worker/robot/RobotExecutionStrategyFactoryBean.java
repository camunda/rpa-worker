package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.ExistingEnvironmentProvider;
import io.camunda.rpa.worker.python.PythonInterpreter;
import io.camunda.rpa.worker.python.PythonRuntimeProperties;
import io.camunda.rpa.worker.python.SystemPythonProvider;
import io.camunda.rpa.worker.util.InternetConnectivityProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
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
	private final ObjectProvider<PythonInterpreter> pythonInterpreter;
	
	@Override
	public Class<?> getObjectType() {
		return RobotExecutionStrategy.class;
	}

	@Override
	public RobotExecutionStrategy getObject() throws Exception {
		return (switch(pythonRuntimeProperties.type()) {
			case Auto -> detectStrategy().block();
			case Python -> pythonStrategy();
			case Static -> staticStrategy();
		});
	}

	private Mono<RobotExecutionStrategy> detectStrategy() {
		return Mono.justOrEmpty(existingEnvironmentProvider.existingPythonEnvironment())
				.doOnNext(_ -> log.atInfo()
						.log("Found existing Python environment, will use Python execution strategy"))
				.map(_ -> pythonStrategy())

				.switchIfEmpty(Mono.defer(systemPythonProvider::systemPython)
						.flatMap(_ -> internetConnectivityProvider.hasConnectivity())
						.filter(it -> it)
						.doOnNext(_ -> log.atInfo()
								.log("Found supported system Python and internet connectivity, will use Python execution strategy"))
						.map(_ -> pythonStrategy()))

				.switchIfEmpty(Mono.defer(() -> Mono.just(System.getProperty("os.name").contains("Windows")))
						.filter(it -> it)
						.flatMap(_ -> internetConnectivityProvider.hasConnectivity())
						.filter(it -> it)
						.doOnNext(_ -> log.atInfo()
								.log("Found Python provision-capable platform and internet connectivity, will use Python execution strategy"))
						.map(_ -> pythonStrategy()))

				.switchIfEmpty(Mono.defer(() -> Mono.just(staticStrategy())
						.doOnNext(_ -> log.atInfo()
								.log("Python strategy not supported (no supported Python or no internet connectivity), will use Static execution strategy"))));
	}

	private RobotExecutionStrategy pythonStrategy() {
		return new PythonRobotExecutionStrategy(processService, pythonInterpreter.getObject());
	}

	private RobotExecutionStrategy staticStrategy() {
		return new StaticRobotExecutionStrategy(processService, io);
	}
}
