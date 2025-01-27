package io.camunda.rpa.worker.robot

import com.fasterxml.jackson.databind.ObjectMapper
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.pexec.ProcessService
import io.camunda.rpa.worker.python.PythonInterpreter
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.util.YamlMapper
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Supplier
import java.util.function.UnaryOperator

class RobotServiceSpec extends Specification implements PublisherUtils {

	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	ObjectMapper objectMapper = new ObjectMapper()
	Path pythonExe = Paths.get("/path/to/python/bin/python")
	PythonInterpreter pythonInterpreter = new PythonInterpreter(pythonExe)
	ProcessService processService = Mock()
	YamlMapper yamlMapper = new YamlMapper(objectMapper)

	@Subject
	RobotService service = new RobotService(io, objectMapper, pythonInterpreter, processService, yamlMapper)

	void "Correctly configures and executes a Robot process"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")
		ProcessService.ExecutionCustomizer executionCustomizer = Mock()
		io.notExists(_) >> true

		and:
		Path workDir = Paths.get("/path/to/workDir/")

		when:
		ExecutionResults r = block service.execute(script, [rpaVar: 'rpa-var-value'], [secretVar: 'secret-var-value'])

		then:
		1 * io.createTempDirectory("robot") >> workDir
		1 * io.createDirectories(workDir.resolve("output"))
		1 * io.createDirectories(workDir.resolve("robot_artifacts"))
		1 * io.writeString(workDir.resolve("main.robot"), "some-script-body", _)
		1 * io.write(workDir.resolve("variables.json"), objectMapper.writeValueAsBytes([rpaVar: 'rpa-var-value']), [])

		and:
		1 * processService.execute(pythonExe, _) >> { _, UnaryOperator<ProcessService.ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content"))
		}

		and:
		1 * executionCustomizer.workDir(workDir) >> executionCustomizer
		1 * executionCustomizer.allowExitCodes(RobotService.ROBOT_TASK_FAILURE_EXIT_CODES) >> executionCustomizer
		1 * executionCustomizer.env("ROBOT_ARTIFACTS", workDir.resolve("robot_artifacts").toAbsolutePath().toString()) >> executionCustomizer
		1 * executionCustomizer.env([secretVar: 'secret-var-value']) >> executionCustomizer
		1 * executionCustomizer.arg("-m") >> executionCustomizer
		1 * executionCustomizer.arg("robot") >> executionCustomizer
		1 * executionCustomizer.arg("--rpa") >> executionCustomizer
		1 * executionCustomizer.arg("--outputdir") >> executionCustomizer
		1 * executionCustomizer.bindArg("outputDir", workDir.resolve("output/main/")) >> executionCustomizer
		1 * executionCustomizer.arg("--variablefile") >> executionCustomizer
		1 * executionCustomizer.bindArg("varsFile", workDir.resolve("variables.json")) >> executionCustomizer
		1 * executionCustomizer.arg("--report") >> executionCustomizer
		1 * executionCustomizer.arg("none") >> executionCustomizer
		1 * executionCustomizer.arg("--logtitle") >> executionCustomizer
		1 * executionCustomizer.arg("Task log") >> executionCustomizer
		1 * executionCustomizer.bindArg("script", workDir.resolve("main.robot")) >> executionCustomizer

		and:
		r.results().values().first().result() == ExecutionResults.Result.PASS
		r.results().values().first().output() == """\
[STDOUT] stdout-content
[STDERR] stderr-content"""
	}

	void "Returns output variables"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		io.createTempDirectory("robot") >> workDir

		and:
		processService.execute(_, _) >> { _, __ ->
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content"))
		}

		when:
		ExecutionResults result = block service.execute(script, [:], [:])

		then:
		1 * io.notExists(workDir.resolve("outputs.yml")) >> false
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [foo: 'bar']

		and:
		result.outputVariables() == [foo: 'bar']

		when:
		ExecutionResults result2 = block service.execute(script, [:], [:])

		then:
		1 * io.notExists(workDir.resolve("outputs.yml")) >> true

		and:
		result2.outputVariables() == [:]
	}

	void "Returns correct ExecutionResult for Robot task failure"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		io.createTempDirectory("robot") >> workDir
		io.notExists(workDir.resolve("outputs.yml")) >> true

		and:
		processService.execute(_, _) >> { _, __ ->
			return Mono.just(new ProcessService.ExecutionResult(
					RobotService.ROBOT_TASK_FAILURE_EXIT_CODES[0], "stdout-content", "stderr-content"))
		}

		when:
		ExecutionResults result = block service.execute(script, [:], [:])

		then:
		result.results().values().first().result() == ExecutionResults.Result.FAIL
	}

	void "Throws correct exception for Robot failure"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		io.createTempDirectory("robot") >> workDir
		io.notExists(workDir.resolve("outputs.yml")) >> true

		and:
		processService.execute(_, _) >> { _, __ ->
			Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_INVALID_INVOKE, "", ""))
		}

		when:
		ExecutionResults result = block service.execute(script, [:], [:])

		then:
		result.results().values().first().result() == ExecutionResults.Result.ERROR
	}
	
	void "Throws correct exception for Robot execution failure"() {
		given:
		RobotScript script = new RobotScript("some-script", "some-script-body")

		and:
		Path workDir = Paths.get("/path/to/workDir/")
		io.createTempDirectory("robot") >> workDir
		io.notExists(workDir.resolve("outputs.yml")) >> true

		and:
		processService.execute(_, _) >> { _, __ ->
			Mono.error(new RuntimeException("Bang!"))
		}

		when:
		block service.execute(script, [:], [:])

		then:
		thrown(RobotErrorException)
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
		io.createTempDirectory("robot") >> workDir
		io.notExists(workDir.resolve("outputs.yml")) >> false
		ProcessService.ExecutionCustomizer executionCustomizer = Mock() {
			_ >> it
		}

		and:
		processService.execute(_, _) >> { _, UnaryOperator<ProcessService.ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content"))
		}

		when:
		ExecutionResults result = block service.execute(script, [before1, before2], [after1, after2], [:], [:])
		
		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("pre_0") }) >> executionCustomizer
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var1: 'val1']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("pre_1") }) >> executionCustomizer
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var2: 'val2']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("main") }) >> executionCustomizer
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var3: 'val3']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("post_0") }) >> executionCustomizer
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var4: 'val4']

		then:
		1 * executionCustomizer.bindArg("script", { it.toString().contains("post_1") }) >> executionCustomizer
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var5: 'val5']

		and:
		["pre_0", "pre_1", "main", "post_0", "post_1"].each { sc ->
			with(result.results().keySet().find { k -> k.startsWith(sc) }) { rr ->
				result.results()[rr].result() == ExecutionResults.Result.PASS
				result.results()[rr].outputVariables()
			}
		}
		
		result.result() == ExecutionResults.Result.PASS
		
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
		io.createTempDirectory("robot") >> workDir
		io.notExists(workDir.resolve("outputs.yml")) >> false
		ProcessService.ExecutionCustomizer executionCustomizer = Mock() {
			_ >> it
		}

		when:
		ExecutionResults result = block service.execute(script, [before1, before2], [after1, after2], [:], [:])

		then:
		1 * processService.execute(_, _) >> { _, UnaryOperator<ProcessService.ExecutionCustomizer> customizer ->
			customizer.apply(executionCustomizer)
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_TASK_FAILURE_EXIT_CODES[0], "stdout-content", "stderr-content"))
		}
		1 * executionCustomizer.bindArg("script", { it.toString().contains("pre_0") }) >> executionCustomizer
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var1: 'val1']

		then:
		0 * processService._

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
		io.createTempDirectory("robot") >> workDir
		io.notExists(workDir.resolve("outputs.yml")) >> false

		when:
		ExecutionResults result = block service.execute(script, [before1, before2], [after1, after2], [:], [:])

		then:
		3 * processService.execute(_, _) >> { _, __ ->
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_SUCCESS, "stdout-content", "stderr-content"))
		}
		3 * io.withReader(workDir.resolve("outputs.yml"), _) >>> [
				[var1: 'val1'], 
				[var2: 'val2'], 
				[var3: 'val3']]
		
		then:
		1 * processService.execute(_, _) >> { _, __ ->
			return Mono.just(new ProcessService.ExecutionResult(RobotService.ROBOT_EXIT_INVALID_INVOKE, "stdout-content", "stderr-content"))
		}
		1 * io.withReader(workDir.resolve("outputs.yml"), _) >> [var3: 'val3']

		then:
		0 * processService._

		and:
		result.results().size() == 4
		result.result() == ExecutionResults.Result.ERROR
		result.outputVariables() == [var1: 'val1', var2: 'val2', var3: 'val3']
	}
}
