package io.camunda.rpa.worker.workspace;

import io.camunda.rpa.worker.io.IO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

	private static final Scheduler cleanupScheduler = Schedulers.newSingle("cleanup");
	
	private final IO io;
	
	private final Queue<Path> preserveLastQueue = new ConcurrentLinkedQueue<>();
	private final Queue<Path> reapQueue = new ConcurrentLinkedQueue<>();


	/// Unconditionally delete a workspace. 
	/// The deletion is scheduled **immediately**; the returned {@link Mono} is used only to signal that a clean-up task 
	/// has ran, and does not need to be subscribed to for the deletion to take place.
	/// 
	/// @param workspace workspace to delete
	/// @return A signal-only Mono signalling completion of the clean-up task
	public Mono<Void> deleteWorkspace(Path workspace) {
		reapQueue.add(workspace);
		return run();
	}

	/// Delete a workspace when it is superseded as the last workspace used. 
	/// The deletion is scheduled **immediately**; the returned {@link Mono} is used only to signal that a clean-up task 
	/// has ran, and does not need to be subscribed to for the deletion to take place. Note that the clean-up task for 
	/// `preserveLast` will not delete the workspace passed in by the argument, as that will become the last one and will
	/// wait to be superseded. 
	///
	/// @param workspace workspace to delete
	/// @return A signal-only Mono signalling completion of the clean-up task
	public Mono<Void> preserveLast(Path workspace) {
		preserveLastQueue.add(workspace);
		return run();
	}
	
	@SuppressWarnings("CallingSubscribeInNonBlockingScope")
	private Mono<Void> run() {
		Mono<Void> cleanupTask = Mono.fromRunnable(this::doRun)
				.subscribeOn(cleanupScheduler)
				.then()
				.doOnError(thrown -> log.atError()
						.setCause(thrown)
						.log("Error cleaning up workspaces"))
				.onErrorComplete()
				.cache();
		cleanupTask.subscribe();
		return cleanupTask;
	}

	private void doRun() {
		while(preserveLastQueue.size() > 1)
			reapQueue.add(preserveLastQueue.remove());
		
		for(Path p = reapQueue.poll(); p != null; p = reapQueue.poll())
			io.deleteDirectoryRecursively(p);
	}
}
