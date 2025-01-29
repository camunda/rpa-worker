package autoversion

import java.nio.file.Path

class AutoVersion {
	
	static String release(Path gradleProperties, Path generic) {
		return new AutoVersion(gradleProperties, generic, new ReleaseVersionCalculator()).newVersion
	}

	static String snapshot(Path gradleProperties, Path generic, Path gitRepo) {
		return new AutoVersion(gradleProperties, generic,
				new SnapshotVersionCalculator(gitRepo)).newVersion
	}

	private final String newVersion

	private AutoVersion(Path gradleProperties, Path generic, VersionCalculator versionCalculator) {
		this.newVersion = writeVersion(gradleProperties, generic, versionCalculator.calculateVersion())
	}

	private String writeVersion(Path gradleProperties, Path generic, String version) {
		Properties p = new Properties()
		gradleProperties.withReader { r ->
			p.load(r)
		}
		p.setProperty("version", version)
		gradleProperties.withWriter { w -> 
			p.store(w, null)
		}
		
		generic.withWriter { w -> 
			w.write(version)
		}
		
		return version
	}

	private String getNewVersion() {
		return newVersion
	}
}
