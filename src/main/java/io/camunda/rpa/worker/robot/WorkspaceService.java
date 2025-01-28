package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {
	
	private final IO io;
	
	private final Queue<Path> preserveLastQueue = new ConcurrentLinkedQueue<>();
	private final Queue<Path> reapQueue = new ConcurrentLinkedQueue<>();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
		Thread thread = Executors.defaultThreadFactory().newThread(r);
		thread.setUncaughtExceptionHandler((throwingThread, thrown) -> log.atError()
				.kv("thread", throwingThread.getName())
				.setCause(thrown)
				.log("Exception in workspace cleanup thread"));
		thread.setName("cleanup");
		return thread;
	});

	/// Unconditionally delete a workspace. The deletion is scheduled **immediately**, and the returned 
	/// signal-only Mono is already hot, even if it is not subscribed to.
	/// 
	/// @param workspace workspace to delete
	/// @return A hot signal-only Mono signalling completion of the deletion
	public Mono<Void> deleteWorkspace(Path workspace) {
		reapQueue.add(workspace);
		return run();
	}

	/// Delete a workspace when it is superseded as the last workspace used. The deletion is scheduled 
	/// **immediately**, and the returned signal-only Mono is already hot, even if it is not subscribed to.
	///
	/// @param workspace workspace to delete
	/// @return A hot signal-only Mono signalling completion of the deletion
	public Mono<Void> preserveLast(Path workspace) {
		preserveLastQueue.add(workspace);
		return run();
	}
	
	private Mono<Void> run() {
		return Mono.fromCompletionStage(CompletableFuture.runAsync(this::doRun, executorService));
	}

	private void doRun() {
		while(preserveLastQueue.size() > 1)
			reapQueue.add(preserveLastQueue.remove());
		
		for(Path p = reapQueue.poll(); p != null; p = reapQueue.poll())
			io.deleteDirectoryRecursively(p);
	}
}
