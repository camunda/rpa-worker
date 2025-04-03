package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.pexec.ProcessService
import org.spockframework.spring.SpringSpy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.mock.env.MockPropertySource
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest/")
class PythonSetupFunctionalSpec extends AbstractFunctionalSpec {
	
	private static final String FTEST_REQUIREMENTS_HASH = "a49629b7d3e9160b64d105075ebd3f1d408c4ccc434b0cd06a9eacb86aeb892f"
	
	private static Path ftestPythonEnv
	
	@Autowired
	ProcessService processService
	
	@Autowired
	PythonInterpreter pythonInterpreter
	
	void setupSpec() {
		ftestPythonEnv = Paths.get("python_ftest/venv/").toAbsolutePath()
		assert ftestPythonEnv.toString().contains("python_ftest")
		alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv.parent)
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
			contains("camunda-utils")
		}
	}

	@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest_extra/")
	@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
	static class PythonSetupWithExtraRequirementsFunctionalSpec extends AbstractFunctionalSpec {

		private static Path ftestPythonEnv

		@Autowired
		ProcessService processService

		@Autowired
		PythonInterpreter pythonInterpreter

		void setupSpec() {
			ftestPythonEnv = Paths.get("python_ftest_extra/venv/").toAbsolutePath()
			assert ftestPythonEnv.toString().contains("python_ftest")
			alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv.parent)
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
			
			and: "Checksum is written for extra requirements"
			ftestPythonEnv.parent.resolve("extra-requirements.last").text == "ec72420df5dfbdce4111f715c96338df3b7cb75f58e478d2449c9720e560de8c"
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

	@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest_extra/")
	@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
	static class PythonSetupWithExtraRequirementsNoChangesWhenAlreadyInstalledFunctionalSpec extends AbstractFunctionalSpec {

		private static Path ftestPythonEnv
		
		@Autowired
		PythonSetupService pythonSetupService

		@SpringSpy
		ProcessService processService

		@Autowired
		PythonInterpreter pythonInterpreter
		

		void setupSpec() {
			ftestPythonEnv = Paths.get("python_ftest_extra/venv/").toAbsolutePath()
			assert ftestPythonEnv.toString().contains("python_ftest")
			alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv.parent)
		}

		void "Skips installing extra requirements when they have not changed"() {
			expect:
			ftestPythonEnv.parent.resolve("extra-requirements.last").text == "ec72420df5dfbdce4111f715c96338df3b7cb75f58e478d2449c9720e560de8c"

			when:
			pythonSetupService.getObject()
			
			then:
			ftestPythonEnv.parent.resolve("extra-requirements.last").text == "ec72420df5dfbdce4111f715c96338df3b7cb75f58e478d2449c9720e560de8c"
			
			and:
			0 * processService._
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

	@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest_extra/")
	@ContextConfiguration(initializers = [StaticPropertyProvidingInitializer])
	static class PythonSetupWithExtraRequirementsChangedInExistingEnvironmentFunctionalSpec extends AbstractFunctionalSpec {

		private static Path ftestPythonEnv

		@Autowired
		PythonSetupService pythonSetupService

		@SpringSpy
		ProcessService processService

		@Autowired
		PythonInterpreter pythonInterpreter
		
		static Path extraRequirementsFile


		void setupSpec() {
			ftestPythonEnv = Paths.get("python_ftest_extra/venv/").toAbsolutePath()
			assert ftestPythonEnv.toString().contains("python_ftest")
			alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv.parent)
		}

		void "Re-installs extra requirements into existing environments when they have changed"() {
			expect:
			ftestPythonEnv.parent.resolve("extra-requirements.last").text == "ec72420df5dfbdce4111f715c96338df3b7cb75f58e478d2449c9720e560de8c"

			when:
			extraRequirementsFile.text = "requests\nchardet"
			
			and:
			pythonSetupService.getObject()

			then:
			ftestPythonEnv.parent.resolve("extra-requirements.last").text == "52c5f32fb0f9e9d921d39386dedd2c1d33ca46cbe87e96c8db9f394a1d3691a7"

			and:
			1 * processService.execute({ it.toString().contains("pip") }, _)
		}

		static class StaticPropertyProvidingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
			@Override
			void initialize(ConfigurableApplicationContext applicationContext) {
				extraRequirementsFile = Files.createTempFile("requirements", ".txt")
				extraRequirementsFile.text = "requests"
				applicationContext.getEnvironment().propertySources.addFirst(new MockPropertySource("pythonReqs")
						.withProperty("camunda.rpa.python.extra-requirements", extraRequirementsFile.toString()))
			}
		}
	}
	
	@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest_basecheck/")
	static class PythonSetupWithBaseRequirementsNoChangesWhenAlreadyInstalledFunctionalSpec extends AbstractFunctionalSpec {

		private static Path ftestPythonEnv

		@Autowired
		PythonSetupService pythonSetupService

		@SpringSpy
		ProcessService processService

		@Autowired
		PythonInterpreter pythonInterpreter

		void setupSpec() {
			ftestPythonEnv = Paths.get("python_ftest_basecheck/venv/").toAbsolutePath()
			assert ftestPythonEnv.toString().contains("python_ftest")
			alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv.parent)
		}

		void "Skips re-installing base requirements when they have not changed"() {
			expect:
			ftestPythonEnv.parent.resolve("requirements.last").text == FTEST_REQUIREMENTS_HASH

			when:
			pythonSetupService.getObject()

			then:
			ftestPythonEnv.parent.resolve("requirements.last").text == FTEST_REQUIREMENTS_HASH

			and:
			0 * processService._
		}

	}

	@TestPropertySource(properties = "camunda.rpa.python.path=python_ftest_basecheck/")
	static class PythonSetupWithBaseRequirementsChangedInExistingEnvironmentFunctionalSpec extends AbstractFunctionalSpec {

		private static Path ftestPythonEnv

		@Autowired
		PythonSetupService pythonSetupService

		@SpringSpy
		ProcessService processService

		@Autowired
		PythonInterpreter pythonInterpreter

		void setupSpec() {
			ftestPythonEnv = Paths.get("python_ftest_basecheck/venv/").toAbsolutePath()
			assert ftestPythonEnv.toString().contains("python_ftest")
			alwaysRealIO.deleteDirectoryRecursively(ftestPythonEnv.parent)
		}

		void "Re-installs extra requirements into existing environments when they have changed"() {
			given:
			ftestPythonEnv.parent.resolve("requirements.last").text = "old-checksum"

			when:
			pythonSetupService.getObject()

			then:
			ftestPythonEnv.parent.resolve("requirements.last").text == FTEST_REQUIREMENTS_HASH

			and:
			1 * processService.execute({ it.toString().contains("pip") }, _)
		}
	}
}
