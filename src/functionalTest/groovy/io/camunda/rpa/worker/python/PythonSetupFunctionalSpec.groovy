package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.pexec.ProcessService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.env.MockPropertySource
import org.springframework.test.context.ContextConfiguration
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
		alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv)
	}

	void "A new Python environment is created (from system Python) and the correct dependencies are available"() {
		expect: "There is a Python environment in the configured directory"
		Files.isDirectory(ftestPythonEnv)
		Files.isRegularFile(ftestPythonEnv.resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pythonExe())))
		
		and: "That is the environment which is made available to the application"
		pythonInterpreter.path().toAbsolutePath() == ftestPythonEnv.resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pythonExe()))

		when:
		ProcessService.ExecutionResult deps = block processService.execute(ftestPythonEnv.resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), c -> c
				.arg("list")
				.inheritEnv())
		
		then:
		with(deps.stdout()) {
			contains("robotframework")
			contains("Camunda")
		}
	}

	@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest/venv_extra/")
	@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
	static class PythonSetupWithExtraRequirementsFunctionalSpec extends AbstractFunctionalSpec {

		private static Path ftestPythonEnv

		@Autowired
		ProcessService processService

		@Autowired
		PythonInterpreter pythonInterpreter

		void setupSpec() {
			ftestPythonEnv = Paths.get("python_ftest/venv_extra/").toAbsolutePath()
			assert ftestPythonEnv.toString().contains("python_ftest")
			alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv)
		}

		void "New Python environments install user requirements when provided"() {
			when:
			ProcessService.ExecutionResult deps = block processService.execute(ftestPythonEnv.resolve(PythonSetupService.pyExeEnv.binDir().resolve(PythonSetupService.pyExeEnv.pipExe())), c -> c
					.arg("list")
					.inheritEnv())

			then: "User extra requirements are installed"
			deps.stdout().contains("requests")
			
			and: "Default requirements are installed"
			deps.stdout().contains("robotframework")
		}

		static class StaticPropertyProvidingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
			@Override
			void initialize(ConfigurableApplicationContext applicationContext) {
				Path extraRequirementsFile = Files.createTempFile("requirements", ".txt")
				extraRequirementsFile.text = "requests"
				applicationContext.getEnvironment().propertySources.addFirst(new MockPropertySource("pythonReqs")
						.withProperty("camunda.rpa.python.extra-requirements", extraRequirementsFile.toString()))
			}
		}
	}
}
