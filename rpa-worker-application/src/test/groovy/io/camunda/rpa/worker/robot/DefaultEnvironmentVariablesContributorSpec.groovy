package io.camunda.rpa.worker.robot

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.workspace.Workspace
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths

class DefaultEnvironmentVariablesContributorSpec extends Specification implements PublisherUtils {

	@Subject
	EnvironmentVariablesContributor contributor = new DefaultEnvironmentVariablesContributor()
	
	void "Returns correct environment variables for workspace and script"() {
		given:
		Workspace workspace = new Workspace("workspace123456", Paths.get("/path/to/workspaces/workspace123456/"))
		PreparedScript script = new PreparedScript("main", new RobotScript("some-script", "script-body"))

		when:
		Map<String, String> vars = block contributor.getEnvironmentVariables(workspace, script)

		then:
		vars == [
				RPA_WORKSPACE: workspace.path().toAbsolutePath().toString(),
				RPA_WORKSPACE_ID: "workspace123456",
				ROBOT_ARTIFACTS: workspace.path().resolve("robot_artifacts").toAbsolutePath().toString(),
				RPA_SCRIPT: "some-script",
				RPA_EXECUTION_KEY: "main",
		]
	}
}
