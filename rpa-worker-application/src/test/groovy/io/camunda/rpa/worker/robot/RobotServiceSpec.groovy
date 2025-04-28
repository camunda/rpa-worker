package io.camunda.rpa.worker.robot

import com.fasterxml.jackson.databind.ObjectMapper
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ExecutionCustomizer
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceService
import io.camunda.rpa.worker.workspace.WorkspaceVariablesManager
import org.springframework.beans.factory.ObjectProvider
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.function.Supplier
import java.util.function.UnaryOperator
import java.util.stream.Stream

class RobotServiceSpec extends Specification implements PublisherUtils {

	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	ObjectMapper objectMapper = new ObjectMapper()
	RobotExecutionStrategy robotExecutionStrategy = Mock()
	WorkspaceVariablesManager workspaceVariablesManager = Mock()
	RobotProperties robotProperties = new RobotProperties(Duration.ofSeconds(3), true)
	WorkspaceService workspaceService = Mock()
	
	EnvironmentVariablesContributor envVarContributor1 = Mock(EnvironmentVariablesContributor)
	EnvironmentVariablesContributor envVarContributor2 = Mock(EnvironmentVariablesContributor)
	ObjectProvider<EnvironmentVariablesContributor> envVarContributors = Stub()

	@Subject
	RobotService service = new RobotService(
			io, 
			objectMapper, 
			robotProperties, 
			workspaceService, 
			Schedulers.single(), 
			envVarContributors, 
			workspaceVariablesManager, 
			robotExecutionStrategy)
	
	RobotExecutionListener executionListener = Mock()

	void "Correctly configures and executes a Robot process"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")
		ExecutionCustomizer executionCustomizer = Mock()
		io.notExists(_) >> true

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace12456", workDir)
		
		and:
		envVarContributors.stream() >> { Stream.of(envVarContributor1, envVarContributor2) }
		
		when:
		ExecutionResults r = block service.execute(
				script,
				[],
				[],
				[rpaVar: 'rpa-var-value']
				,
				null, 
				[executionListener]
				,
				[:], 
				null)

		then:
		1 * workspaceService.createWorkspace(null, [:]) >> workspace
		1 * io.createDirectories(workDir.resolve("output"))
		1 * io.createDirectories(workDir.resolve("robot_artifacts"))
		1 * io.writeString(workDir.resolve("main.robot"), "some-script-body", _)
		1 * io.write(workDir.resolve("variables.json"), objectMapper.writeValueAsBytes([rpaVar: 'rpa-var-value']), [])

		and:
		1 * robotExecutionStrategy.executeRobot(_) >> { UnaryOperator<ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content", Duration.ofSeconds(3)))
		}
		
		and:
		1 * envVarContributor1.getEnvironmentVariables(workspace, _) >> Mono.just([ENV_VAR_ONE: 'envVarOneValue'])
		1 * envVarContributor2.getEnvironmentVariables(workspace, _) >> Mono.just([ENV_VAR_TWO: 'envVarTwoValue'])

		and:
		1 * executionCustomizer.workDir(workDir) >> executionCustomizer
		1 * executionCustomizer.allowExitCodes(RobotService.ROBOT_TASK_FAILURE_EXIT_CODES) >> executionCustomizer
		
		1 * executionCustomizer.inheritEnv() >> executionCustomizer
		1 * executionCustomizer.env([ENV_VAR_ONE: 'envVarOneValue', ENV_VAR_TWO: 'envVarTwoValue']) >> executionCustomizer

		1 * executionCustomizer.arg("--rpa") >> executionCustomizer
		1 * executionCustomizer.arg("--outputdir") >> executionCustomizer
		1 * executionCustomizer.bindArg("outputDir", workDir.resolve("output/main/")) >> executionCustomizer
		1 * executionCustomizer.arg("--variablefile") >> executionCustomizer
		1 * executionCustomizer.bindArg("varsFile", workDir.resolve("variables.json")) >> executionCustomizer
		1 * executionCustomizer.arg("--report") >> executionCustomizer
		1 * executionCustomizer.arg("none") >> executionCustomizer
		1 * executionCustomizer.arg("--logtitle") >> executionCustomizer
		1 * executionCustomizer.arg("Task log") >> executionCustomizer
		1 * executionCustomizer.conditionalArg({ fn -> fn.getAsBoolean() }, "-X") >> executionCustomizer
		1 * executionCustomizer.timeout(robotProperties.defaultTimeout()) >> executionCustomizer
		1 * executionCustomizer.bindArg("script", workDir.resolve("main.robot")) >> executionCustomizer
		
		and:
		1 * workspaceVariablesManager.beforeScriptExecution(workspace, _)
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())

		and:
		r.results().values().first().result() == ExecutionResults.Result.PASS
		r.results().values().first().output() == """\
[STDOUT] stdout-content
[STDERR] stderr-content"""
		r.results().values().first().duration() == Duration.ofSeconds(3)
		r.duration() == Duration.ofSeconds(3)
		
		and:
		1 * executionListener.afterRobotExecution(workspace)
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [:]
		1 * workspaceVariablesManager.afterRobotExecution(workspace)
	}

	void "Returns output variables"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace

		and:
		robotExecutionStrategy.executeRobot(_) >> { _ ->
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content", Duration.ZERO))
		}

		when:
		ExecutionResults result = block service.execute(script, [:], null, [], null)

		then:
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [foo: 'bar']

		and:
		result.outputVariables() == [foo: 'bar']
	}

	void "Returns correct ExecutionResult for Robot task failure"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace
		workspaceVariablesManager.getVariables(workspace.id()) >> [:]

		and:
		robotExecutionStrategy.executeRobot(_) >> { _ ->
			return Mono.just(new ProcessService.ExecutionResult(
					RobotService.ROBOT_TASK_FAILURE_EXIT_CODES[0], "stdout-content", "stderr-content", Duration.ZERO))
		}

		when:
		ExecutionResults result = block service.execute(script, [:], null, [executionListener], null)

		then:
		result.results().values().first().result() == ExecutionResults.Result.FAIL
		
		and:
		1 * executionListener.afterRobotExecution(workspace)
	}

	void "Throws correct exception for Robot failure"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace
		workspaceVariablesManager.getVariables(workspace.id()) >> [:]

		and:
		robotExecutionStrategy.executeRobot(_) >> { _ ->
			Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_INVALID_INVOKE, "", "", Duration.ZERO))
		}

		when:
		ExecutionResults result = block service.execute(script, [:], null, [executionListener], null)

		then:
		result.results().values().first().result() == ExecutionResults.Result.ERROR
		
		and:
		1 * executionListener.afterRobotExecution(workspace)
	}
	
	void "Throws correct exception for Robot execution failure"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace
		workspaceVariablesManager.getVariables(workspace.id()) >> [:]

		and:
		robotExecutionStrategy.executeRobot(_) >> { _ ->
			Mono.error(new RuntimeException("Bang!"))
		}

		when:
		block service.execute(script, [:], null, [executionListener], null)

		then:
		thrown(RobotErrorException)
		
		and:
		1 * executionListener.afterRobotExecution(workspace)
	}
	
	void "Runs before and after scripts and aggregates results"() {
		given:
		RobotScript before1 = new RobotScript("some-script", "some-script-body")
		RobotScript before2 = new RobotScript("some-script", "some-script-body")
		RobotScript script = new RobotScript("some-script", "some-script-body")
		RobotScript after1 = new RobotScript("some-script", "some-script-body")
		RobotScript after2 = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace
		ExecutionCustomizer executionCustomizer = Mock() {
			_ >> it
		}

		and:
		robotExecutionStrategy.executeRobot(_) >> { UnaryOperator<ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content", Duration.ofSeconds(1)))
		}

		when:
		ExecutionResults result = block service.execute(script, [before1, before2], [after1, after2], [:], null, [executionListener], [:], null)
		
		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("pre_0") }) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var1: 'val1']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("pre_1") }) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var2: 'val2']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("main") }) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var3: 'val3']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("post_0") }) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var4: 'val4']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("post_1") }) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var5: 'val5']

		and:
		["pre_0", "pre_1", "main", "post_0", "post_1"].each { sc ->
			with(result.results().keySet().find { k -> k.startsWith(sc) }) { rr ->
				result.results()[rr].result() == ExecutionResults.Result.PASS
				result.results()[rr].outputVariables()
				result.results()[rr].duration() == Duration.ofSeconds(1)
			}
		}
		
		result.result() == ExecutionResults.Result.PASS
		result.duration() == Duration.ofSeconds(5)
		
		result.outputVariables() == [
				var1: 'val1',
				var2: 'val2',
				var3: 'val3',
				var4: 'val4',
				var5: 'val5'
		]
	}

	void "Stops execution and returns correct aggregate results for pre/post script failure"() {
		given:
		RobotScript before1 = new RobotScript("some-script", "some-script-body")
		RobotScript before2 = new RobotScript("some-script", "some-script-body")
		RobotScript script = new RobotScript("some-script", "some-script-body")
		RobotScript after1 = new RobotScript("some-script", "some-script-body")
		RobotScript after2 = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace
		ExecutionCustomizer executionCustomizer = Mock() {
			_ >> it
		}

		when:
		ExecutionResults result = block service.execute(script, [before1, before2], [after1, after2], [:], null, [executionListener], [:], null)

		then:
		1 * robotExecutionStrategy.executeRobot(_) >> { UnaryOperator<ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_TASK_FAILURE_EXIT_CODES[0], "stdout-content", "stderr-content", Duration.ZERO))
		}
		1 * executionCustomizer.bindArg("script", { it.toString().contains("pre_0") }) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, robotProperties.defaultTimeout())
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var1: 'val1']

		then:
		0 * robotExecutionStrategy._

		and:
		result.results().size() == 1
		result.result() == ExecutionResults.Result.FAIL
		result.outputVariables() == [var1: 'val1']
	}

	void "Stops execution and returns correct aggregate results for pre/post script error"() {
		given:
		RobotScript before1 = new RobotScript("some-script", "some-script-body")
		RobotScript before2 = new RobotScript("some-script", "some-script-body")
		RobotScript script = new RobotScript("some-script", "some-script-body")
		RobotScript after1 = new RobotScript("some-script", "some-script-body")
		RobotScript after2 = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace

		when:
		ExecutionResults result = block service.execute(script, [before1, before2], [after1, after2], [:], null, [], [:], null)

		then:
		3 * robotExecutionStrategy.executeRobot(_) >> { _ ->
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content", Duration.ZERO))
		}
		3 * workspaceVariablesManager.getVariables(workspace.id()) >>> [
				[var1: 'val1'], 
				[var2: 'val2'], 
				[var3: 'val3']]
		
		then:
		1 * robotExecutionStrategy.executeRobot(_) >> { _ ->
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_INVALID_INVOKE, "stdout-content", "stderr-content", Duration.ZERO))
		}
		1 * workspaceVariablesManager.getVariables(workspace.id()) >> [var3: 'val3']

		then:
		0 * robotExecutionStrategy._

		and:
		result.results().size() == 4
		result.result() == ExecutionResults.Result.ERROR
		result.outputVariables() == [var1: 'val1', var2: 'val2', var3: 'val3']
	}

	void "Configures timeout when set"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		Workspace workspace = new Workspace("workspace123456", workDir)
		workspaceService.createWorkspace(null, [:]) >> workspace
		workspaceVariablesManager.getVariables(workspace.id()) >> [:]
		ExecutionCustomizer executionCustomizer = Mock() {
			_ >> it
		}

		and:
		robotExecutionStrategy.executeRobot(_) >> { UnaryOperator<ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_TASK_FAILURE_EXIT_CODES[0], "stdout-content", "stderr-content", Duration.ZERO))
		}

		when:
		block service.execute(script, [:], Duration.ofMinutes(3), [executionListener], null)

		then:
		1 * executionCustomizer.timeout(Duration.ofMinutes(3)) >> executionCustomizer
		1 * executionListener.beforeScriptExecution(workspace, Duration.ofMinutes(3))
	}
}
