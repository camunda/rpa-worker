package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.workspace.WorkspaceFile;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
class TaskTestingReportRenderer {
	
	private final IO io;

	public Mono<WorkspaceFile> renderReportForTaskTesting(WorkspaceFile htmlReport) {
		return io.supply(() -> Try.withResources(WebClient::new)
				.of(webClient -> {
					webClient.getOptions().setJavaScriptEnabled(true);
					webClient.getOptions().setCssEnabled(true);
					webClient.getOptions().setThrowExceptionOnScriptError(false);

					HtmlPage page = webClient.getPage("file://%s".formatted(htmlReport.path().toAbsolutePath().toString()));

					webClient.waitForBackgroundJavaScript(Duration.ofSeconds(2).toMillis());
					page.executeJavaScript("""
							let expandAllButtons = document.querySelectorAll(".suite .expand");
							for(let i = 0; i < expandAllButtons.length; i++) expandAllButtons.item(i).click();
							setTimeout(() => {
								$("body").attr("data-theme", "light");
								$("script").remove();
								$(".element-header-toggle").remove();
								$(".expand").remove();
								$(".collapse").remove();
								$("#theme-toggle").remove();
							}, 1000);
							""");
					webClient.waitForBackgroundJavaScript(Duration.ofSeconds(2).toMillis());

					String html = page.asXml();

					Path reportNoScript = htmlReport.path().resolveSibling("report_noscript.html");
					io.writeString(reportNoScript, html);
					return new WorkspaceFile(htmlReport.workspace(), htmlReport.contentType(), io.size(reportNoScript), reportNoScript);
				}).get());
	}
}
