package io.camunda.rpa.worker.pexec;

import lombok.Getter;

@Getter
public class ProcessTimeoutException extends RuntimeException {
	
	private final String stdout;
	private final String stderr;

	public ProcessTimeoutException(String stdout, String stderr) {
		super("Process timed out");
		this.stdout = stdout;
		this.stderr = stderr;
	}
}
