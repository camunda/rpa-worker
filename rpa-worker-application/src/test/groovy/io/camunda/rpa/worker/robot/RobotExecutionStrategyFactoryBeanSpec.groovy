package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.ExistingEnvironmentProvider
import io.camunda.rpa.worker.python.PythonInterpreter
import io.camunda.rpa.worker.python.PythonRuntimeProperties
import io.camunda.rpa.worker.python.SystemPythonProvider
import io.camunda.rpa.worker.util.InternetConnectivityProvider
import org.springframework.beans.factory.ObjectProvider
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Paths

class RobotExecutionStrategyFactoryBeanSpec extends Specification {

	ProcessService processService
	ExistingEnvironmentProvider existingEnvironmentProvider = Mock()
	SystemPythonProvider systemPythonProvider = Mock()
	InternetConnectivityProvider internetConnectivityProvider = Mock()
	IO io = Stub() {
		run(_) >> { Mono.empty() }
	}
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))
	ObjectProvider<PythonInterpreter> pythonInterpreterProvider = Stub() {
		getObject() >> pythonInterpreter
	}

	@Subject
	Closure<RobotExecutionStrategyFactoryBean> factoryBeanFactory = { PythonRuntimeProperties.PythonRuntimeEnvironment env ->
		PythonRuntimeProperties runtimeProperties = PythonRuntimeProperties.builder().type(env).build()
		new RobotExecutionStrategyFactoryBean(
				runtimeProperties,
				processService,
				existingEnvironmentProvider,
				systemPythonProvider,
				internetConnectivityProvider,
				io, 
				pythonInterpreterProvider)
	}
	
	void "Returns correct strategy for static config - Python"() {
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Python).getObject()
		
		then:
		r instanceof PythonRobotExecutionStrategy
	}

	void "Returns correct strategy for static config - Static"() {
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Static).getObject()

		then:
		r instanceof StaticRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because existing"() {
		given:
		platformIsNotWindows()
		
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.of(pythonInterpreter.path())
		
		and:
		0 * systemPythonProvider._
		0 * internetConnectivityProvider._
		
		and:
		r instanceof PythonRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because system Python and internet good"() {
		given:
		platformIsNotWindows()
		
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.just("python")
		1 * internetConnectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		r instanceof PythonRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Python because Windows and internet good"() {
		given:
		platformIsWindows()
		
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.empty()
		1 * internetConnectivityProvider.hasConnectivity() >> Mono.just(true)

		and:
		r instanceof PythonRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Static because no system Python"() {
		given:
		platformIsNotWindows()
		
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.empty()
		0 * internetConnectivityProvider.hasConnectivity() 

		and:
		r instanceof StaticRobotExecutionStrategy
	}

	@RestoreSystemProperties
	void "Returns correct strategy for auto - Static because no internet"() {
		given:
		platformIsNotWindows()
		
		when:
		RobotExecutionStrategy r = factoryBeanFactory(PythonRuntimeProperties.PythonRuntimeEnvironment.Auto).getObject()

		then:
		1 * existingEnvironmentProvider.existingPythonEnvironment() >> Optional.empty()
		1 * systemPythonProvider.systemPython() >> Mono.just("python")
		1 * internetConnectivityProvider.hasConnectivity() >> Mono.just(false)

		and:
		r instanceof StaticRobotExecutionStrategy
	}
	
	private static void platformIsNotWindows() {
		System.setProperty("os.name", "Linux")
	}
	
	private static void platformIsWindows() {
		System.setProperty("os.name", "Windows")
	}
}
