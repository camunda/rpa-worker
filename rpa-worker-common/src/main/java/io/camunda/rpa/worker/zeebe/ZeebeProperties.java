package io.camunda.rpa.worker.zeebe;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Set;

@ConfigurationProperties("camunda.rpa.zeebe")
@Builder(toBuilder = true)
record ZeebeProperties(
		String rpaTaskPrefix,
		Set<String> workerTags,
		URI authEndpoint, 
		int maxConcurrentJobs) { }
