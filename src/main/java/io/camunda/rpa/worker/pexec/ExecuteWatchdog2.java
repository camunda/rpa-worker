package io.camunda.rpa.worker.pexec;

import lombok.Getter;
import org.apache.commons.exec.ExecuteWatchdog;

@Getter
class ExecuteWatchdog2 extends ExecuteWatchdog {
	
	private Process process;
	
	@SuppressWarnings("deprecation")
	public ExecuteWatchdog2() {
		super(-1);
	}

	@Override
	public synchronized void start(Process processToMonitor) {
		super.start(processToMonitor);
		this.process = processToMonitor;
	}
}
