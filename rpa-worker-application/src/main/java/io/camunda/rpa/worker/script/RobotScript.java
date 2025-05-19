package io.camunda.rpa.worker.script;

import lombok.Builder;
import lombok.Singular;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@Builder(toBuilder = true)
public record RobotScript(String id, String body, @Singular Map<Path, String> files) {

	public RobotScript {
		if(files == null) files = Collections.emptyMap();
	}
}
