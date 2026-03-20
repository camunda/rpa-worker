package io.camunda.rpa.worker.zeebe

import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.workspace.Workspace
import io.camunda.rpa.worker.workspace.WorkspaceFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

class TaskTestingReportRendererSpec extends Specification implements PublisherUtils {
	
	IO io = Mock() {
		supply(_) >> { Supplier fn -> Mono.fromSupplier(fn) }
	}
	
	@Subject
	TaskTestingReportRenderer reportRenderer = new TaskTestingReportRenderer(io)

	Path workspacePath 
	Workspace workspace
	Path logPath 
	WorkspaceFile originalHtmlReport

	void setup() {
		workspacePath = Files.createTempDirectory("workspace")
		workspace = new Workspace("workspace-id", this.workspacePath)
		logPath = workspacePath.resolve("log.html")
		logPath.text = TaskTestingReportRendererSpec.classLoader.getResource("tasklog.html").text
		originalHtmlReport = new WorkspaceFile(workspace, "text/html", 123, logPath)
	}

	void "Writes properly re-rendered report into Workspace"() {
		when:
		WorkspaceFile newReport = block reportRenderer.renderReportForTaskTesting(originalHtmlReport)
		
		then:
		1 * io.writeString(logPath.resolveSibling("report_noscript.html"), _ as String) >> { Path p, String newHtml, _ ->
			verifyAll {
				Document doc = Jsoup.parse(newHtml)
				doc.getElementById("s1-t1-k2-k2-k1-k1-k1")
				doc.getElementsByClass(".closed").isEmpty()
				doc.getElementsByTag("body").attr("data-theme") == "light"
				doc.getElementsByTag("script").isEmpty()
				doc.getElementsByClass(".element.header-toggle").isEmpty()
				doc.getElementsByClass(".expand").isEmpty()
				doc.getElementsByClass(".collapse").isEmpty()
				! doc.getElementById("theme-toggle")
			}
		}
		newReport
		1 * io.size(logPath.resolveSibling("report_noscript.html")) >> 234
		
		and:
		newReport.workspace() == originalHtmlReport.workspace()
		newReport.contentType() == originalHtmlReport.contentType()
		newReport.path() == originalHtmlReport.path().resolveSibling("report_noscript.html")
		newReport.size() == 234
	}
}
