package io.camunda.rpa.worker.pexec;

import java.io.IOException;

public class AbortProcessSilentlyException extends IOException {

	public AbortProcessSilentlyException() {
		super("The process was deliberately aborted");
	}
}