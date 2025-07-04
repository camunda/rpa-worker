package io.camunda.rpa.worker.script;

import feign.FeignException;
import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.util.ArchiveUtils;
import io.camunda.rpa.worker.zeebe.RpaResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ScriptRepository {
	
	String getKey();
	Mono<RobotScript> findById(String id);

	default Mono<RobotScript> getById(String id) {
		return findById(id)
				.onErrorComplete(FeignException.NotFound.class)
				.switchIfEmpty(Mono.error(new ScriptNotFoundException(id)));
	}
	
	static Mono<RobotScript> resourceToScript(IO io, RpaResource rpa) {
		Function<Map<String, String>, Mono<Map<Path, byte[]>>> filesSupplier = raw -> {
			if (raw == null) return Mono.just(Collections.emptyMap());

			return Flux.fromIterable(raw.entrySet())
					.flatMap(kv ->
							ArchiveUtils.inflateBase64(io, kv.getValue())
									.map(bytes -> Map.entry(Paths.get(kv.getKey()), bytes)))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		};

		return Mono.just(RobotScript.builder()
						.id(rpa.id())
						.body(rpa.script()))
				.zipWhen(_ -> filesSupplier.apply(rpa.files()), RobotScript.RobotScriptBuilder::files)
				.map(RobotScript.RobotScriptBuilder::build);
	}
}
