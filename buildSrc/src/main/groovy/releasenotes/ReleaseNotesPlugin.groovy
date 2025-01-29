package releasenotes

import groovy.text.GStringTemplateEngine
import groovy.text.TemplateEngine
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

import javax.inject.Inject
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest

class ReleaseNotesPlugin implements Plugin<Project> {
	@Override
	void apply(Project target) {
		Jar bootJar = target.tasks.named("bootJar", Jar).get()
		Task grh = target.tasks.register("generateReleaseHeader", GenerateReleaseHeaderTask, bootJar).get()
		grh.dependsOn(bootJar)
	}
	
	static class GenerateReleaseHeaderTask extends DefaultTask {
		
		private final Jar bootJar

		@Inject
		GenerateReleaseHeaderTask(Jar bootJar) {
			this.bootJar = bootJar
		}

		@TaskAction
		void run() {
			URL template = getClass().getResource("/releasenotes_header.md")
			TemplateEngine te = new GStringTemplateEngine()
			Writable cooked = te.createTemplate(template).make([
					jarFilename: bootJar.outputs.files.singleFile.name,
					jarHash    : sha256(bootJar.outputs.files.singleFile.toPath())
			])
			Path out = project.layout.buildDirectory.getAsFile().get().toPath().resolve("releasenotes_header.md")
			out.withWriter { w -> cooked.writeTo(w) }
		}
	}
	
	static String sha256(Path path) {
		MessageDigest md = MessageDigest.getInstance("SHA-256")
		path.withInputStream { is ->
			DigestOutputStream os = new DigestOutputStream(OutputStream.nullOutputStream(), md)
			is.transferTo(os)
		}
		return HexFormat.of().formatHex(md.digest())
	}
}
