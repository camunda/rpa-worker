package io.camunda.rpa.worker.util;

import io.camunda.rpa.worker.net.WebClientProvisioner;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Slf4j
public class InternetConnectivityProvider {

	static final String TEST_URL = "https://console.cloud.camunda.io/robots.txt";
	
	private final WebClient webClient;
	
	public InternetConnectivityProvider(WebClientProvisioner webClientProvisioner) {

		this.webClient = webClientProvisioner.webClient(_ -> {}, c -> c
				.responseTimeout(Duration.ofMillis(1_500))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_500));
	}
	
	public Mono<Boolean> hasConnectivity() {
		return webClient
				.get()
				.uri(getTestUrl())
				.retrieve()
				.toBodilessEntity()
				.map(re -> re.getStatusCode() == HttpStatus.OK)
				.onErrorComplete()
				.defaultIfEmpty(false)
				.doOnNext(c -> log.atInfo()
						.kv("connected", c)
						.log("Connectivity check"));
	}
	
	protected String getTestUrl() {
		return TEST_URL;
	}
}
