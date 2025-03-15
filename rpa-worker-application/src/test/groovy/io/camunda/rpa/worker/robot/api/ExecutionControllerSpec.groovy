package io.camunda.rpa.worker.robot.api

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.pexec.ProcessControl
import io.camunda.rpa.worker.robot.RobotService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject

class ExecutionControllerSpec extends Specification implements PublisherUtils {
	
	RobotService robotService = Stub()
	
	@Subject
	ExecutionController controller = new ExecutionController(robotService)
	
	void "Returns not found when no matching execution"() {
		given:
		robotService.findExecutionByWorkspace(_) >> Optional.empty()

		when:
		ResponseEntity<?> r = block controller.abort("doesnt-exist", new AbortExecutionRequest(null))
		
		then:
		r.statusCode == HttpStatus.NOT_FOUND
	}

	void "Aborts process"(boolean silent) {
		given:
		ProcessControl processControl = Mock()
		robotService.findExecutionByWorkspace("workspace") >> Optional.of(processControl)

		when:
		ResponseEntity<?> r = block controller.abort("workspace", new AbortExecutionRequest(silent))

		then:
		1 * processControl.abort(silent)
		r.statusCode == HttpStatus.NO_CONTENT
		
		where:
		silent << [true, false]
	}
}
