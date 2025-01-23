package io.camunda.rpa.worker.script;

public class ScriptNotFoundException extends RuntimeException {
	public ScriptNotFoundException(String scriptKey) {
		super("Script not found: %s".formatted(scriptKey));
	}
}
