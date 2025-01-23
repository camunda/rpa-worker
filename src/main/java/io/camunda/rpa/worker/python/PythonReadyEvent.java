package io.camunda.rpa.worker.python;

import org.springframework.context.ApplicationEvent;

class PythonReadyEvent extends ApplicationEvent {
	public PythonReadyEvent(PythonInterpreter pythonInterpreter) {
		super(pythonInterpreter);
	}
}
