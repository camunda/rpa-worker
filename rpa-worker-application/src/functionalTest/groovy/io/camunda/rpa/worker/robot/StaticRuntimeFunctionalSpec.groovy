package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.PythonSetupService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["camunda.rpa.python-runtime.type=static"])
class StaticRuntimeFunctionalSpec extends AbstractFunctionalSpec {
	
	@SpringBean
	PythonSetupService pythonSetupService = Stub()
	
	@Autowired
	RobotExecutionStrategy robotExecutionStrategy
	
	void "Uses static runtime when configured"() {
		expect:
		robotExecutionStrategy.toString().contains("StaticRobotExecutionStrategy")

		when:
		ProcessService.ExecutionResult result = block robotExecutionStrategy.executeRobot(c -> c
				.arg("--version")
				.silent())
		
		then:
		result.exitCode() == RobotService.ROBOT_EXIT_HELP_OR_VERSION_REQUEST
	}
}
