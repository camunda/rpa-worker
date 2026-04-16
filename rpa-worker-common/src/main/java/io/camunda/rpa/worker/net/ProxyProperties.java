package io.camunda.rpa.worker.net;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camunda.rpa.proxy")
record ProxyProperties(ConfigMode configMode, PreferredSource preferredSource) {
	enum ConfigMode {
		AllOrNothing,
		Merge,
		None;
	}
	
	enum PreferredSource {
		Java,
		Generic
	}
}
