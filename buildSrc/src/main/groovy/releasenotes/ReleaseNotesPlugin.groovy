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
		private final Map<Path, String> hashes = [:]

		@Inject
		GenerateReleaseHeaderTask(Jar bootJar, Path projectRoot) {
			this.bootJar = bootJar
			this.projectRoot = projectRoot
		}

		@TaskAction
		void run() {
			URL template = getClass().getResource("/releasenotes_header.md")
			TemplateEngine te = new GStringTemplateEngine()

			Path exeJar = findFile(projectRoot, "rpa-worker-application-${project.version}", ".jar")
			Path elementTemplate = findElementTemplate(projectRoot)
			Writable cooked = te.createTemplate(template).make([
					jarFilename: exeJar.fileName.toString(),
					jarHash    : sha256AndCache(exeJar),
					version    : project.version.toString(),
					nativeLinuxAmd64Hash: sha256AndCache(findFile(projectRoot, "linux_amd64")),
					nativeWin32Amd64Hash: sha256AndCache(findFile(projectRoot, "win32_amd64")),
					nativeDarwinAmd64Hash: sha256AndCache(findFile(projectRoot, "darwin_amd64")),
					nativeDarwinAarch64Hash: sha256AndCache(findFile(projectRoot, "darwin_aarch64")),
					elementTemplateFilename: elementTemplate.fileName.toString(),
					elementTemplateHash: sha256AndCache(elementTemplate),
			])
			Path out = project.layout.buildDirectory.getAsFile().get().toPath().resolve("releasenotes_header.md")
			Files.createDirectories(out.parent)
			out.withWriter { w -> cooked.writeTo(w) }
			
			
			Path hashesDir = project.layout.buildDirectory.getAsFile().get().toPath().resolve("hashes")
			Files.createDirectories(hashesDir)
			hashes.each { k, v ->
				hashesDir.resolve(k.fileName.toString() + ".sha256").text = v
			}
		}
		
		private String sha256AndCache(Path path) {
			String hash = sha256(path)
			hashes[path] = hash
			return hash
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
	
	static Path findFile(Path root, String... queries) {
		return Files.walk(root)
				.filter(Files::isRegularFile)
				.filter(p -> Arrays.stream(queries).allMatch { query ->  p.getFileName().toString().contains(query) })
				.findFirst()
				.orElseThrow(() -> new NoSuchElementException("Could not find file matching queries %s".formatted(queries)))
	}

	static Path findElementTemplate(Path root) {
		return Files.walk(root)
				.filter(Files::isRegularFile)
				.filter(p -> p.fileName.toString() != "rpa-connector.json"
						&& p.fileName.toString().contains("rpa-connector")
						&& p.fileName.toString().contains(".json"))
				.findFirst()
				.orElseThrow()
	}
}
