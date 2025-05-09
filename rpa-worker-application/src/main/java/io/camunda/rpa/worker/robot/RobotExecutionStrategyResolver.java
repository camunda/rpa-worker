package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.python.ExistingEnvironmentProvider;
import io.camunda.rpa.worker.python.PythonRuntimeProperties;
import io.camunda.rpa.worker.python.SystemPythonProvider;
import io.camunda.rpa.worker.util.InternetConnectivityProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static io.camunda.rpa.worker.python.PythonRuntimeProperties.PythonRuntimeEnvironment.Python;
import static io.camunda.rpa.worker.python.PythonRuntimeProperties.PythonRuntimeEnvironment.Static;

@Component
@RequiredArgsConstructor
@Slf4j
class RobotExecutionStrategyResolver implements FactoryBean<ResolvedRobotExecutionStrategyType> {
	
	private final PythonRuntimeProperties pythonRuntimeProperties;
	private final ExistingEnvironmentProvider existingEnvironmentProvider;
	private final SystemPythonProvider systemPythonProvider;
	private final InternetConnectivityProvider internetConnectivityProvider;
	
	@Override
	public Class<?> getObjectType() {
		return ResolvedRobotExecutionStrategyType.class;
	}

	@Override
	public ResolvedRobotExecutionStrategyType getObject() throws Exception {
		return (switch(pythonRuntimeProperties.type()) {
			case Auto -> detectStrategy().block();
			case Python -> pythonStrategy();
			case Static -> staticStrategy();
		});
	}

	private Mono<ResolvedRobotExecutionStrategyType> detectStrategy() {
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
				
				.publishOn(Schedulers.boundedElastic())

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

	private ResolvedRobotExecutionStrategyType pythonStrategy() {
		return () -> Python;
	}

	private ResolvedRobotExecutionStrategyType staticStrategy() {
		return () -> Static;
	}
}
