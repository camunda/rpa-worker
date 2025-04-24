package io.camunda.rpa.worker;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;


@Configuration
@ImportRuntimeHints(NativeHints.class)
class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerPattern("runtime/robot");
		hints.resources().registerPattern("runtime/robot.exe");
	}
}
