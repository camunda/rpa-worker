package autoversion

import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import java.nio.file.Path

class AutoVersionPlugin implements Plugin<Project> {
	@Override
	void apply(Project target) {
		target.tasks.register("setVersion", SetVersionTask)
	}
	
	static class SetVersionTask extends DefaultTask {
		@TaskAction
		void setVersion() {
			Path props = project.rootDir.toPath().resolve("gradle.properties")
			Path generic = project.rootDir.toPath().resolve("version.txt")

			String newVersion = System.getenv("GITHUB_REF_TYPE")?.equalsIgnoreCase("tag")
					? AutoVersion.release(props, generic)
					: AutoVersion.snapshot(
						props,
						generic,
						project.projectDir.toPath())

			try {
				Version.parse(newVersion)
			} catch (ParseException pex) {
				throw new GradleException("Provided version '${newVersion}' is not formatted correctly", pex)
			}

			logger.quiet("New version is ${newVersion}")
		}
	}
}
