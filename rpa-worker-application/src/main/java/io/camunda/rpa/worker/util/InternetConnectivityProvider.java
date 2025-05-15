package io.camunda.rpa.worker.util;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Component
@Slf4j
public class InternetConnectivityProvider {

	static final String TEST_URL = "https://console.cloud.camunda.io/robots.txt";
	
	private final WebClient webClient;
	
	public InternetConnectivityProvider(WebClient.Builder webClientBuilder) {

		HttpClient client = HttpClient.create()
				.responseTimeout(Duration.ofMillis(1_500))
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_500);
		
		this.webClient = webClientBuilder
				.clientConnector(new ReactorClientHttpConnector(client))
				.build();
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
