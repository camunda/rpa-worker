package io.camunda.rpa.worker.pexec;

import lombok.Getter;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Watchdog;

import java.util.concurrent.TimeoutException;

@Getter
class ExecuteWatchdog2 extends ExecuteWatchdog {
	
	private Process process;
	
	@SuppressWarnings("deprecation")
	public ExecuteWatchdog2() {
		super(-1);
	}

	@SuppressWarnings("deprecation")
	public ExecuteWatchdog2(long timeoutMillis) {
		super(timeoutMillis);
	}

	@Override
	public synchronized void start(Process processToMonitor) {
		super.start(processToMonitor);
		this.process = processToMonitor;
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
}
