package io.camunda.rpa.worker.workspace;

import io.camunda.rpa.worker.io.IO;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WorkspaceService implements ApplicationListener<ApplicationReadyEvent> {

	private final IO io;

	@Getter(AccessLevel.PACKAGE)
	private Path workspacesDir;

	@Override
	public void onApplicationEvent(ApplicationReadyEvent ignored) {
		doInit();
	}

	WorkspaceService doInit() {
		workspacesDir = io.createTempDirectory("rpa-workspaces");
		return this;
	}

	public Path createWorkspace() {
		Path workspace = io.createTempDirectory(workspacesDir, "workspace");
		io.writeString(workspace.resolve(".workspace"), workspace.getFileName().toString(), StandardOpenOption.CREATE_NEW);
		return workspace;
	}

	public Optional<Path> getById(String id) {
		Path perhapsWorkspace = workspacesDir.resolve(id).normalize().toAbsolutePath();
		if ( ! perhapsWorkspace.startsWith(workspacesDir))
			return Optional.empty();

		if (io.notExists(perhapsWorkspace) || ! io.isDirectory(perhapsWorkspace))
			return Optional.empty();

		Path workspaceIdFile = perhapsWorkspace.resolve(".workspace");
		if (io.notExists(workspaceIdFile))
			return Optional.empty();

		if ( ! io.readString(workspaceIdFile).trim().equals(id))
			return Optional.empty();

		return Optional.of(perhapsWorkspace);
	}

	public Stream<WorkspaceFile> getWorkspaceFiles(String workspaceId) {
		return getById(workspaceId)
				.stream()
				.flatMap(p -> io.walk(p)
						.filter(io::isRegularFile)
						.filter(pp -> ! pp.getFileName().toString().startsWith(".")))
				.map(p -> new WorkspaceFile(io.probeContentType(p), io.size(p), p));
	}

	public Optional<WorkspaceFile> getWorkspaceFile(String workspaceId, String path) {
		record WorkspaceAndRequestedFile(Path workspace, Path requestedFile) { }

		return getById(workspaceId)
				.map(workspace -> new WorkspaceAndRequestedFile(workspace, workspace.resolve(path).normalize().toAbsolutePath()))
				.filter(wr -> wr.requestedFile().startsWith(wr.workspace()))
				.map(WorkspaceAndRequestedFile::requestedFile)
				.filter(io::exists)
				.map(workspaceFile -> new WorkspaceFile(
						io.probeContentType(workspaceFile),
						io.size(workspaceFile),
						workspaceFile));
	}
}
