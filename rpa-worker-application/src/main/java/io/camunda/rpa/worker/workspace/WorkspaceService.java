package io.camunda.rpa.worker.workspace;

import io.camunda.rpa.worker.io.IO;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

	private final IO io;

	private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();
	
	@Getter(AccessLevel.PACKAGE)
	private Path workspacesDir;

	@PostConstruct
	WorkspaceService init() {
		workspacesDir = io.createTempDirectory("rpa-workspaces");
		return this;
	}

	public Workspace createWorkspace() {
		return createWorkspace(Collections.emptyMap());
	}
	
	public Workspace createWorkspace(Map<String, Object> properties) {
		Path workspacePath = io.createTempDirectory(workspacesDir, "workspace");
		String workspaceID = workspacePath.getFileName().toString();
		io.writeString(workspacePath.resolve(".workspace"), workspaceID, StandardOpenOption.CREATE_NEW);
		workspaces.put(workspaceID, new Workspace(workspaceID, workspacePath, properties));
		return workspaces.get(workspaceID);
	}

	public Optional<Workspace> getById(String id) {
		return Optional.ofNullable(workspaces.get(id))
				.filter(w -> io.exists(w.path()))
				.filter(w -> io.isDirectory(w.path()));
	}

	public Stream<WorkspaceFile> getWorkspaceFiles(String workspaceId) {
		Optional<Workspace> workspace = getById(workspaceId);
		return workspace
				.stream()
				.flatMap(p -> io.walk(p.path())
						.filter(io::isRegularFile)
						.filter(pp -> ! pp.getFileName().toString().startsWith(".")))
				.map(p -> new WorkspaceFile(workspace.get(), io.probeContentType(p), io.size(p), p));
	}

	public Optional<WorkspaceFile> getWorkspaceFile(String workspaceId, String path) {
		record WorkspaceAndRequestedFile(Workspace workspace, Path requestedFile) { }

		return getById(workspaceId)
				.map(workspace -> new WorkspaceAndRequestedFile(workspace, workspace.path().resolve(path).normalize().toAbsolutePath()))
				.filter(wr -> wr.requestedFile().startsWith(wr.workspace().path()))
				.filter(wr -> io.exists(wr.requestedFile()))
				.map(wr -> new WorkspaceFile(
						wr.workspace(),
						io.probeContentType(wr.requestedFile()),
						io.size(wr.requestedFile()),
						wr.requestedFile()));
	}
}
