package autoversion

import io.vavr.collection.Stream
import io.vavr.collection.Traversable
import io.vavr.control.Option
import io.vavr.control.Try
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref

import java.nio.file.Path

class SnapshotVersionCalculator implements VersionCalculator {

	private final Path gitRepo

	SnapshotVersionCalculator(Path gitRepo) {
		this.gitRepo = gitRepo
	}

	@Override
	String calculateVersion() {
		return Try.withResources(() -> Git.open(gitRepo.toFile()))
				.of(this::doCalculateVersion)
				.get()
	}

	@SuppressWarnings("deprecation")
	private String doCalculateVersion(Git git) {
		Option<Map.Entry<String, Ref>> lastTag = Option.ofOptional(git.getRepository().getTags().entrySet().stream()
				.filter(tag -> ! (tag.getKey().contains("-") || tag.getKey().contains("+")))
				.findFirst())

		Option<Integer> commitsSinceLastTag = lastTag.toTry().mapTry(lt ->
				git.log().addRange(
						lt.getValue().getObjectId(),
						git.getRepository().resolve("HEAD")).call())
				.map(Stream::ofAll)
				.map(Traversable::size)
				.toOption()

		String currentBranch = Optional.ofNullable(System.getenv("GITHUB_REF_NAME"))
				.filter { ! it.endsWith("/merge") }
				.map(this::cleanBranchForVersion)
				.map(s -> "branch-" + s)
				.or {
					Optional.ofNullable(System.getenv("GITHUB_REF_NAME"))
							.map { ref -> "merge-${ref.split('/')[1]}" }
				}
				.orElse("unknown")

		return lastTag.map(Map.Entry::getKey).getOrElse("0.0.1") +
				"-" + currentBranch +
				commitsSinceLastTag.map(n -> ".c" + n).getOrElse("") +
				".g" + ObjectId.fromString(System.getenv("GITHUB_SHA")).abbreviate(7).name()
	}

	private String cleanBranchForVersion(String branch) {
		return branch
				.replaceAll("/", "-")
				.replaceAll("_", "-")
				.replaceAll("[^0-9A-Za-z-]", "")
	}
}
