package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.pexec.ProcessService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest/venv/")
class PythonSetupFunctionalSpec extends AbstractFunctionalSpec {
	
	private static Path ftestPythonEnv
	
	@Autowired
	ProcessService processService
	
	@Autowired
	PythonInterpreter pythonInterpreter
	
	void setupSpec() {
		ftestPythonEnv = Paths.get("python_ftest/venv/").toAbsolutePath()
		assert ftestPythonEnv.toString().contains("python_ftest")
		"rm -rf ${ftestPythonEnv}".execute().waitFor()
	}

	void "A new Python environment is created (from system Python) and the correct dependencies are available"() {
		expect: "There is a Python environment in the configured directory"
		Files.isDirectory(ftestPythonEnv)
		Files.isRegularFile(ftestPythonEnv.resolve("bin/python"))
		
		and: "That is the environment which is made available to the application"
		pythonInterpreter.path().toAbsolutePath() == ftestPythonEnv.resolve("bin/python")

		when:
		ProcessService.ExecutionResult deps = block processService.execute(ftestPythonEnv.resolve("bin/pip"), c -> c
				.arg("list")
				.inheritEnv())
		
		then:
		with(deps.stdout()) {
			contains("robotframework")
			contains("Camunda")
		}
	}
}
