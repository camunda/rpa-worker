package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.camunda.rpa.worker.script.RobotScript
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1
import io.camunda.zeebe.client.api.response.ActivatedJob
import org.springframework.test.context.TestPropertySource
import spock.lang.Ignore
import spock.lang.IgnoreIf

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConcurrencyFunctionalSpec extends AbstractFunctionalSpec {
	
	static final Closure<String> DELAYED_FILE_SCRIPT_TEMPLATE = { jobNum ->
		"""\
*** Settings ***
Library             OperatingSystem

*** Tasks ***
Write files for job #${jobNum}
	Create File    written_first_\${jobNum}.txt
	Sleep    1
	Create File    written_last_\${jobNum}.txt
"""
	}
	
	static final String SIMPLE_OUTPUT_VARIABLE_SCRIPT =
			'''\
*** Settings ***
Library             Camunda

*** Tasks ***
Set an output variable
    Set Output Variable     myJobNumber      ${jobNum}
'''
	
	static final String LONG_RUNNING_SCRIPT_THAT_DOESNT_TIMEOUT = '''\
*** Tasks ***
Don't do very much
	Sleep    6
'''
	
	static List<RobotScript> getConcurrencyScripts() {
		return [
				RobotScript.builder().id("delayed_file_0").body(DELAYED_FILE_SCRIPT_TEMPLATE(0)).build(),
				RobotScript.builder().id("delayed_file_1").body(DELAYED_FILE_SCRIPT_TEMPLATE(1)).build(),
		]
	}

	@TestPropertySource(properties = "camunda.rpa.zeebe.max-concurrent-jobs=1")
	static class JobLimitFunctionalSpec extends AbstractScriptRepositoryProvidingZeebeFunctionalSpec {

		@Override
		List<RobotScript> getScripts() {
			return [
					RobotScript.builder().id("long_script").body(LONG_RUNNING_SCRIPT_THAT_DOESNT_TIMEOUT).build(), 
					*getConcurrencyScripts()]
		}

		void "Queue has no timeout"() {
			given:
			withNoSecrets()
			
			and:
			CountDownLatch handlersDidFinish = new CountDownLatch(2)
			workspaceCleanupService.deleteWorkspace(_) >> {
				handlersDidFinish.countDown()
			}

			when:
			zeebeJobService.handleJob(anRpaJob([jobNum: 0], "long_script", [:], 0)).subscribe()
			zeebeJobService.handleJob(anRpaJob([jobNum: 1], "long_script", [:], 1)).subscribe()
			handlersDidFinish.awaitRequired(20, TimeUnit.SECONDS)

			then:
			2 * zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
				it.variables(_) >> it
				2 * send()
			}
		}
	}

	@TestPropertySource(properties = "camunda.rpa.zeebe.max-concurrent-jobs=2")
	static class ConcurrentJobFunctionalSpec extends AbstractScriptRepositoryProvidingZeebeFunctionalSpec {

		@Override
		List<RobotScript> getScripts() {
			return getConcurrencyScripts()
		}

		void "Allows concurrent jobs when configured"() {
			given:
			withNoSecrets()
			CountDownLatch handlerDidFinish = new CountDownLatch(2)
			List<Workspace> workspaces = []
			workspaceCleanupService.deleteWorkspace(_) >> { Workspace w ->
				workspaces << w
				handlerDidFinish.countDown()
				return null
			}

			and:
			zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
				variables(_) >> it
			}

			when:
			zeebeJobService.handleJob(anRpaJob([jobNum: 0], "delayed_file_0", [:], 0)).subscribe()
			Thread.sleep(250) 
			zeebeJobService.handleJob(anRpaJob([jobNum: 1], "delayed_file_1", [:], 1)).subscribe()
			handlerDidFinish.awaitRequired(10, TimeUnit.SECONDS)

			then:
			workspaces
					.collectMany { w -> Files.list(w.path()).filter(Files::isRegularFile).filter(p -> p.getFileName().toString().startsWith("written_")).toList() }
					.sort { p -> Files.readAttributes(p, BasicFileAttributes).creationTime().toInstant() }
					.collect { p -> p.fileName.toString() - ".txt" } == [

					"written_first_0",
					"written_first_1",
					"written_last_0",
					"written_last_1",
			]
		}
	}

	@TestPropertySource(properties = "camunda.rpa.zeebe.max-concurrent-jobs=256")
	@IgnoreIf({ System.getenv("CI") })
	static class ScaleFunctionalSpec extends AbstractScriptRepositoryProvidingZeebeFunctionalSpec {

		@Override
		List<RobotScript> getScripts() {
			return [RobotScript.builder().id("simple_output").body(SIMPLE_OUTPUT_VARIABLE_SCRIPT).build()]
		}

		@Ignore
		void "Scale test"() {
			given:
			withNoSecrets()
			CountDownLatch handlerDidFinish = new CountDownLatch(256)
			List<Workspace> workspaces = []
			workspaceCleanupService.deleteWorkspace(_) >> { Workspace w ->
				workspaces << w
				handlerDidFinish.countDown()
				return null
			}

			when:
			256.times { jobNum ->
				zeebeJobService.handleJob(anRpaJob([jobNum: jobNum], "simple_output", [:], jobNum)).subscribe()
			}
			handlerDidFinish.awaitRequired(20, TimeUnit.SECONDS)

			then:
			zeebeClient.newCompleteCommand(_ as ActivatedJob) >> Mock(CompleteJobCommandStep1) {
				256 * it.variables({ m -> m.containsKey("myJobNumber") }) >> it
				256 * send()
			}
		}
	}
}
