package io.camunda.rpa.worker.zeebe;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.rpa.worker.script.RobotScript;
import io.camunda.rpa.worker.script.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
class ZeebeResourceScriptRepository implements ScriptRepository {

	private final ResourceClient resourceClient;
	
	/**
	 * The script cache. 
	 * As scripts are immutable, and updating a script would mean a new version, which
	 * would mean a new resource key, we can cache the scripts for a long time. 
	 * The only reason to evict at all is to prevent the cache ballooning on extremely 
	 * long-lived Workers, but we expire based on access, not write, so we are never 
	 * throwing away cached scripts that are in active use. 
	 */
	private final LoadingCache<String, Mono<RobotScript>> scriptCache = Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofHours(8))
			.build(this::doFindById);

	@Override
	public String getKey() {
		return "zeebe";
	}

	@Override
	public Mono<RobotScript> findById(String id) {
		return scriptCache.get(id);
	}

	private Mono<RobotScript> doFindById(String id) {
		return resourceClient.getRpaResource(id)
				.map(rpa -> new RobotScript(rpa.id(), rpa.script()))
				.cache();
	}
}
