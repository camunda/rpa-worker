package io.camunda.rpa.worker.pexec;

import io.vavr.control.Try;
import lombok.Getter;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Watchdog;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Getter
class ExecuteWatchdog2 extends ExecuteWatchdog {

	private final Optional<ProcessExecutionListener> executionListener;
	
	private Process process;
	
	public ExecuteWatchdog2(Optional<ProcessExecutionListener> executionListener) {
		this(-1, executionListener);
	}

	@SuppressWarnings("deprecation")
	public ExecuteWatchdog2(long timeoutMillis, Optional<ProcessExecutionListener> executionListener) {
		super(timeoutMillis);
		this.executionListener = executionListener;
	}

	@Override
	public synchronized void start(Process processToMonitor) {
		super.start(processToMonitor);
		this.process = processToMonitor;
		executionListener.ifPresent(l -> l.processStarted(this::abortSilently));
	}

	@Override
	public synchronized void timeoutOccured(final Watchdog w) {
		try {
			if (process != null)
				process.exitValue();
		}
		catch (final IllegalThreadStateException ignored) {
			if (isWatching())
				process.toHandle().destroy();
		}
		
		try {
			super.timeoutOccured(w);
		} catch(Exception ignored) { }
		
		failedToStart(new TimeoutException());
	}

	private void abortSilently() {
		process.toHandle().destroy();
		Try.run(() -> process.waitFor(10, TimeUnit.SECONDS));
		process.toHandle().destroyForcibly();
		failedToStart(new AbortProcessSilentlyException());
	}
}
