package releasenotes

import groovy.text.GStringTemplateEngine
import groovy.text.TemplateEngine
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest

class ReleaseNotesPlugin implements Plugin<Project> {
	@Override
	void apply(Project target) {
		Jar bootJar = target.tasks.named("bootJar", Jar).get()
		target.tasks.register(
				"generateReleaseHeader", 
				GenerateReleaseHeaderTask, 
				bootJar, 
				target.rootDir.toPath()).get()
	}
	
	static class GenerateReleaseHeaderTask extends DefaultTask {
		
		private final Jar bootJar
		private final Path projectRoot

		@Inject
		GenerateReleaseHeaderTask(Jar bootJar, Path projectRoot) {
			this.bootJar = bootJar
			this.projectRoot = projectRoot
		}

		@TaskAction
		void run() {
			URL template = getClass().getResource("/releasenotes_header.md")
			TemplateEngine te = new GStringTemplateEngine()

			Path exeJar = findFile(projectRoot, ".jar")
			Writable cooked = te.createTemplate(template).make([
					jarFilename: exeJar.fileName.toString(),
					jarHash    : sha256(exeJar),
					version    : project.version.toString(),
					nativeLinuxAmd64Hash: sha256(findFile(projectRoot, "linux_amd64")),
					nativeWin32Amd64Hash: sha256(findFile(projectRoot, "win32_amd64")),
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
	
	static Path findFile(Path root, String query) {
		return Files.walk(root)
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().contains(query))
				.findFirst()
				.orElseThrow()
	}
}
