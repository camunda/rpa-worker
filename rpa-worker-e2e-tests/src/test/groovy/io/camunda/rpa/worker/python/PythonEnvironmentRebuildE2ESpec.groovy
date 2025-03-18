package io.camunda.rpa.worker.python

import io.camunda.rpa.worker.AbstractE2ESpec
import io.camunda.rpa.worker.io.DefaultIO
import reactor.core.scheduler.Schedulers

import java.nio.file.Files
import java.nio.file.Paths

class PythonEnvironmentRebuildE2ESpec extends AbstractE2ESpec {
	
	static io.camunda.rpa.worker.io.IO io = new DefaultIO(Schedulers.boundedElastic())
	
	void "Rebuilds the Python environment when a startup check fails"() {
		when:
		stopWorker()

		and:
		io.deleteDirectoryRecursively(Files.list(Paths.get(System.getProperty("user.dir"))
				.resolve("python/venv/lib/"))
				.filter { p -> Files.isDirectory(p) && p.fileName.toString().startsWith("python") }
				.findFirst()
				.get()
				.resolve("site-packages/robot/"))

		then:
		startWorker()
	}
}
