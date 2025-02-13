package io.camunda.rpa.worker.script;

import io.camunda.rpa.worker.io.IO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

@Repository
@RequiredArgsConstructor
class LocalLibraryScriptRepository implements ScriptRepository {
	
	private final ScriptProperties scriptProperties;
	private final IO io;
	
	@PostConstruct
	LocalLibraryScriptRepository init() {
		io.createDirectories(scriptProperties.path());
		return this;
	}

	@Override
	public String getKey() {
		return "local";
	}

	@Override
	public Mono<RobotScript> findById(String id) {
		Path script = scriptProperties.path().resolve("%s.robot".formatted(id));
		return io.supply(() -> io.notExists(script) ? null : new RobotScript(id, io.readString(script)));
	}

	@Override
	public Mono<RobotScript> save(RobotScript robotScript) {
		Path script = scriptProperties.path().resolve("%s.robot".formatted(robotScript.id()));
		return io.supply(() -> io.writeString(script, robotScript.body()))
				.thenReturn(robotScript);
	}
}
