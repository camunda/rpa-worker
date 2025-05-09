package io.camunda.rpa.worker.robot


import io.camunda.rpa.worker.python.ExistingEnvironmentProvider
import io.camunda.rpa.worker.python.PythonInterpreter
import io.camunda.rpa.worker.python.PythonRuntimeProperties
import io.camunda.rpa.worker.python.SystemPythonProvider
import io.camunda.rpa.worker.util.InternetConnectivityProvider
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Paths

class RobotExecutionStrategyResolverSpec extends Specification {

	ExistingEnvironmentProvider existingEnvironmentProvider = Mock()
	SystemPythonProvider systemPythonProvider = Mock()
	InternetConnectivityProvider internetConnectivityProvider = Mock()
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))

	@Subject
	Closure<RobotExecutionStrategyResolver> factoryBeanFactory = { PythonRuntimeProperties.PythonRuntimeEnvironment env ->
		PythonRuntimeProperties runtimeProperties = PythonRuntimeProperties.builder().type(env).build()
		new RobotExecutionStrategyResolver(
				runtimeProperties,
				existingEnvironmentProvider,
				systemPythonProvider,
				internetConnectivityProvider)
	}
	
	void "Returns correct strategy for static config - Python"() {
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Python).getObject().getType()
		
		then:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Python
	}

	void "Returns correct strategy for static config - Static"() {
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Static).getObject().getType()

		then:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Static
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because existing"() {
		given:
		platformIsNotWindows()
		
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject().getType()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(pythonInterpreter.path())
		
		and:
		0 * systemPythonProvider._
		0 * internetConnectivityProvider._
		
		and:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Python
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because system Python and internet good"() {
		given:
		platformIsNotWindows()
		
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject().getType()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.just("python")
		1 * internetConnectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Python
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because Windows and internet good"() {
		given:
		platformIsWindows()
		
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject().getType()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.empty()
		1 * internetConnectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Python
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Static because no system Python"() {
		given:
		platformIsNotWindows()
		
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject().getType()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.empty()
		0 * internetConnectivityProvider.hasConnectivity() 

		and:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Static
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Static because no internet"() {
		given:
		platformIsNotWindows()
		
		when:
		PythonRuntimeProperties.PythonRuntimeEnvironment r = factoryBeanFactory(
				PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject().getType()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.just("python")
		1 * internetConnectivityProvider.hasConnectivity() >> Mono.just(false)

		and:
		r == PythonRuntimeProperties.PythonRuntimeEnvironment.Static
	}
	
	private static void platformIsNotWindows() {
		System.setProperty("os.name", "Linux")
	}
	
	private static void platformIsWindows() {
		System.setProperty("os.name", "Windows")
	}
}
