package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.util.ApplicationRestarter
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths
import java.time.Duration
import java.util.function.UnaryOperator

class PythonStartupCheckSpec extends Specification implements PublisherUtils {
	
	PythonSetupService pythonSetupService = Mock()
	ApplicationRestarter applicationRestarter = Mock()
	ProcessService processService = Mock()
	PythonInterpreter pythonInterpreter = new PythonInterpreter(Paths.get("/path/to/python"))
	
	@Subject
	PythonStartupCheck check = new PythonStartupCheck(pythonSetupService, applicationRestarter, processService, pythonInterpreter)

	ExecutionCustomizer executionCustomizer = Mock() {
		_ >> it
	}

	void "Returns ready event on successful check"() {
		when:
		PythonReadyEvent event = block check.check()
		
		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			c.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(0, "Python 3.12.8", "", Duration.ZERO))
		}
		
		and:
		1 * executionCustomizer.noFail() >> executionCustomizer
		
		and:
		event
	}

	void "Purges Python environment and restarts once when check is unsuccessful (post-invoke)"() {
		when:
		check.check().subscribe()

		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			c.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(255, "", "Python is poorly", Duration.ZERO))
		}
		
		and:
		1 * pythonSetupService.purgeEnvironment() >> Mono.empty()
		1 * applicationRestarter.restart()
		
		when:
		check.check().subscribe()

		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			c.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(255, "", "Python is poorly", Duration.ZERO))
		}

		and:
		0 * pythonSetupService.purgeEnvironment()
		0 * applicationRestarter.restart()
	}

	void "Purges Python environment and restarts once when check is unsuccessful (pre-invoke)"() {
		when:
		check.check().subscribe()

		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			return Mono.error(new IOException("No Python"))
		}

		and:
		1 * pythonSetupService.purgeEnvironment() >> Mono.empty()
		1 * applicationRestarter.restart()

		when:
		check.check().subscribe()

		then:
		1 * processService.execute(pythonInterpreter.path(), _) >> { __, UnaryOperator<ExecutionCustomizer> c ->
			return Mono.error(new IOException("No Python"))
		}

		and:
		0 * pythonSetupService.purgeEnvironment()
		0 * applicationRestarter.restart()
	}
}
