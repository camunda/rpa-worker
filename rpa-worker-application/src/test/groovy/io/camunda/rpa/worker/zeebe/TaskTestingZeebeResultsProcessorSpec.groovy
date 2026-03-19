package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.files.FilesService
import io.camunda.rpa.worker.files.ZeebeDocumentDescriptor
import io.camunda.rpa.worker.robot.ExecutionResults
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceFile
import io.camunda.rpa.worker.workspace.WorkspaceService
import io.camunda.zeebe.client.api.response.ActivatedJob
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path
import java.time.Duration

class TaskTestingZeebeResultsProcessorSpec extends Specification implements PublisherUtils {
	
	WorkspaceService workspaceService = Stub()
	FilesService filesService = Stub()
	ActivatedJob job = Stub() {
		getBpmnProcessId() >> "process-id"
		getProcessInstanceKey() >> 123L
	}
	Workspace workspace = new Workspace(null, null)
	ExecutionResults originalResults = new ExecutionResults(
			[:],
			ExecutionResults.Result.PASS,
			[originalOutputVariable: true],
			workspace,
			Duration.ofSeconds(1))

	TaskTestingReportRenderer reportRenderer = Stub()
	
	void "Does nothing when not Task Testing"() {
		given:
		@Subject TaskTestingZeebeResultsProcessor processor = new TaskTestingZeebeResultsProcessor(workspaceService, filesService, [:], job, reportRenderer)

		when:
		ExecutionResults results = block processor.withExecutionResults(originalResults)
		
		then:
		results == originalResults
	}
	
	void "Uploads log file automatically and sets output variable when Task Testing"() {
		given:
		ZeebeJobInfo jobInfo = new ZeebeJobInfo("process-id", 123L)
		WorkspaceFile originalHtmlReport = new WorkspaceFile(workspace, "text/html", 123, Stub(Path))
		workspaceService.getWorkspaceFile(workspace, "output/main/log.html") >> Optional.of(originalHtmlReport)
		
		and:
		WorkspaceFile renderedTaskTestingReport = new WorkspaceFile(workspace, "text/html", 234, Stub(Path))
		ZeebeDocumentDescriptor zdd = new ZeebeDocumentDescriptor("store-id", "document-id", FilesService.toMetadata(renderedTaskTestingReport, jobInfo), "content-hash")
		reportRenderer.renderReportForTaskTesting(originalHtmlReport) >> Mono.just(renderedTaskTestingReport)
		filesService.uploadDocument(renderedTaskTestingReport, FilesService.toMetadata(renderedTaskTestingReport, jobInfo)) >> Mono.just(zdd)
		
		and:
		@Subject TaskTestingZeebeResultsProcessor processor = new TaskTestingZeebeResultsProcessor(workspaceService, filesService, [(TaskTestingZeebeResultsProcessor.TASK_TESTING_VARIABLE_NAME): true], job, reportRenderer)

		when:
		ExecutionResults results = block processor.withExecutionResults(originalResults)

		then:
		results.result() == originalResults.result()
		results.results() == originalResults.results()
		results.outputVariables() == [
				(TaskTestingZeebeResultsProcessor.TASK_TESTING_LOG_OUTPUT_VARIABLE_NAME): zdd, 
				*: originalResults.outputVariables()]
		results.workspace() == originalResults.workspace()
		results.duration() == originalResults.duration()
	}
}
