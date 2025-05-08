package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.ExistingEnvironmentProvider
import io.camunda.rpa.worker.python.PythonInterpreter
import io.camunda.rpa.worker.python.PythonRuntimeProperties
import io.camunda.rpa.worker.python.PythonRuntimeProperties.PythonRuntimeEnvironment
import io.camunda.rpa.worker.python.SystemPythonProvider
import io.camunda.rpa.worker.util.InternetConnectivityProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Paths

@DirtiesContext
class ExecutionStrategyFunctionalSpec extends AbstractFunctionalSpec {
	
	@Autowired
	PythonRuntimeProperties pythonRuntimeProperties
	
	@Autowired
	ProcessService processService
	
	ExistingEnvironmentProvider existingEnvironmentProvider = Stub()
	
	InternetConnectivityProvider connectivityProvider = Stub()
	
	SystemPythonProvider systemPythonProvider = Stub()
	
	@Autowired
	WebClient.Builder webClientBuilder
	
	@Autowired
	IO io
	
	@Autowired
	ObjectProvider<PythonInterpreter> pythonProvider

	void "Returns correct strategy for static config - Python"() {
		given:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Python),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()
		
		then:
		r instanceof PythonRobotExecutionStrategy
	}

	void "Returns correct strategy for static config - Static"() {
		given:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Static),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()

		then:
		r instanceof StaticRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because existing"() {
		given:
		platformIsNotWindows()
		
		and:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(Paths.get("/path/to/python"))
		
		and:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Auto),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()

		then:
		r instanceof PythonRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because system Python and internet good"() {
		given:
		platformIsNotWindows()
		
		and:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		systemPythonProvider.systemPython() >> Mono.just("python3")
		connectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Auto),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()

		then:
		r instanceof PythonRobotExecutionStrategy
	}
	
	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because Windows and internet good"() {
		given:
		platformIsWindows()
		
		and:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		systemPythonProvider.systemPython() >> Mono.empty()
		connectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Auto),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()

		then:
		r instanceof PythonRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Static because no system Python"() {
		given:
		platformIsNotWindows()
		
		and:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		systemPythonProvider.systemPython() >> Mono.empty()
		connectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Auto),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()

		then:
		r instanceof StaticRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Static because no internet"() {
		given:
		platformIsNotWindows()
		
		and:
		existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		systemPythonProvider.systemPython() >> Mono.just("python3")
		InternetConnectivityProvider connectivityProvider = new InternetConnectivityProvider(webClientBuilder) {
			@Override
			protected String getTestUrl() {
				return "http://localhost:4448"
			}
		}
		
		and:
		@Subject
		RobotExecutionStrategyFactoryBean factoryBean = new RobotExecutionStrategyFactoryBean(
				cfg(PythonRuntimeEnvironment.Auto),
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				connectivityProvider,
				io,
				pythonProvider)

		when:
		RobotExecutionStrategy r = factoryBean.getObject()
		
		then:
		r instanceof StaticRobotExecutionStrategy
	}
	
	private PythonRuntimeProperties cfg(PythonRuntimeEnvironment type) {
		return pythonRuntimeProperties.toBuilder().type(type).build()
	}

	private static void platformIsNotWindows() {
		System.setProperty("os.name", "Linux")
	}

	private static void platformIsWindows() {
		System.setProperty("os.name", "Windows")
	}
}
