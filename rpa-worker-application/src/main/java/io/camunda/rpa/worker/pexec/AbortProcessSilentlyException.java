package io.camunda.rpa.worker.pexec;

import java.io.IOException;

class AbortProcessSilentlyException extends IOException {

	public AbortProcessSilentlyException() {
		super("The process was deliberately aborted");
	}
}