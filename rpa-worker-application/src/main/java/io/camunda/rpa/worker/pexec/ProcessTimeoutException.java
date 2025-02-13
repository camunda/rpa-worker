package io.camunda.rpa.worker.pexec;

import lombok.Getter;

@Getter
public class ProcessTimeoutException extends RuntimeException {
	
	private final String stdout;
	private final String stderr;

	public ProcessTimeoutException(String stdout, String stderr) {
		this(stdout, stderr, null);
	}
	
	public ProcessTimeoutException(String stdout, String stderr, Throwable cause) {
		super("Process timed out", cause);
		this.stdout = stdout;
		this.stderr = stderr;
	}
}
