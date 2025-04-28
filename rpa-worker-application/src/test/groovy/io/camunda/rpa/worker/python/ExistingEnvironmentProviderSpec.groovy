package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.io.IO
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths

class ExistingEnvironmentProviderSpec extends Specification {
	
	Path pathToPython = Paths.get("/path/to/python")
	
	PythonProperties pythonProperties = PythonProperties.builder().path(pathToPython).build()
	IO io = Mock()
	
	@Subject
	ExistingEnvironmentProvider provider = new ExistingEnvironmentProvider(pythonProperties, io)
	
	void "Returns empty when no existing environment"() {
		when:
		Optional<Path> r = provider.existingPythonEnvironment()
		
		then:
		1 * io.notExists(pathToPython.resolve("venv/pyvenv.cfg")) >> true
		
		and:
		r.empty
	}
	
	void "Returns existing environment when found"() {
		when:
		Optional<Path> r = provider.existingPythonEnvironment()

		then:
		1 * io.notExists(pathToPython.resolve("venv/pyvenv.cfg")) >> false

		and:
		with(r.get()) {
			it == pathToPython
					.resolve("venv/")
					.resolve(PythonSetupService.pyExeEnv.binDir())
					.resolve(PythonSetupService.pyExeEnv.pythonExe())
		}
	}
}
