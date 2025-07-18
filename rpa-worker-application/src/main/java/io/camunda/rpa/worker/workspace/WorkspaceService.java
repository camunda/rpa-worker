package io.camunda.rpa.worker.workspace;

import io.camunda.rpa.worker.io.IO;
import io.vavr.control.Try;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

	private final IO io;

	private final Map<String, Workspace> workspaces = new ConcurrentHashMap<>();
	private final Set<LockableFileChannel> locks = new CopyOnWriteArraySet<>();
	
	@Getter(AccessLevel.PACKAGE)
	private Path workspacesDir;

	@PostConstruct
	WorkspaceService init() {
		workspacesDir = io.createTempDirectory("rpa-workspaces");
		ensureWorkspacesDirExistsAndIsLocked();
		return this;
	}

	public Workspace createWorkspace() {
		return createWorkspace(null, Collections.emptyMap());
	}

	public Workspace createWorkspace(String affinityKey) {
		return createWorkspace(affinityKey, Collections.emptyMap());
	}


	public Workspace createWorkspace(String affinityKey, Map<String, Object> properties) {
		ensureWorkspacesDirExistsAndIsLocked();
		Path workspacePath = io.createTempDirectory(workspacesDir, "workspace");
		String workspaceID = workspacePath.getFileName().toString();
		io.writeString(workspacePath.resolve(".workspace"), workspaceID, StandardOpenOption.CREATE_NEW);
		workspaces.put(workspaceID, new Workspace(workspaceID, workspacePath, properties, affinityKey));
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
		return getById(workspaceId)
				.flatMap(workspace -> doGetWorkspaceFile(workspace, path));
	}

	public Optional<WorkspaceFile> getWorkspaceFile(Workspace workspace, String path) {
		return doGetWorkspaceFile(workspace, path);
	}

	private Optional<WorkspaceFile> doGetWorkspaceFile(Workspace workspace, String path) {
		record WorkspaceAndRequestedFile(Workspace workspace, Path requestedFile) { }

		return Optional.of(new WorkspaceAndRequestedFile(workspace, workspace.path().resolve(path).normalize().toAbsolutePath()))
				.filter(wr -> wr.requestedFile().startsWith(wr.workspace().path()))
				.filter(wr -> io.exists(wr.requestedFile()))
				.map(wr -> new WorkspaceFile(
						wr.workspace(),
						io.probeContentType(wr.requestedFile()),
						io.size(wr.requestedFile()),
						wr.requestedFile()));
	}
	
	private record LockableFileChannel(FileChannel fileChannel, FileLock fileLock) {
		static LockableFileChannel locking(Path path) {
			return Try.of(() -> new RandomAccessFile(path.toFile(), "rw"))
					.mapTry(fh -> new LockableFileChannel(fh.getChannel(), fh.getChannel().lock()))
					.get();
		}
	}
	
	private void ensureWorkspacesDirExistsAndIsLocked() {
		io.createDirectories(workspacesDir);
		Path lockFile = workspacesDir.resolve(".lock");
		Try.of(() -> LockableFileChannel.locking(lockFile))
				.onSuccess(locks::add)
				.recover(OverlappingFileLockException.class, _ -> null)
				.onFailure(thrown -> log.atError()
						.setCause(thrown)
						.kv("lockFile", lockFile)
						.log("Couldn't lock workspaces dir"));
	}
	
	@PreDestroy
	private void destroy() {
		locks.forEach(lock -> {
			Try.run(() -> lock.fileLock().release());
			Try.run(() -> lock.fileChannel().close());
		});
	}
}
