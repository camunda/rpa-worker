package autoversion

class ReleaseVersionCalculator implements VersionCalculator {
	@Override
	String calculateVersion() {
		return System.getenv("GITHUB_REF_NAME")
	}
}
