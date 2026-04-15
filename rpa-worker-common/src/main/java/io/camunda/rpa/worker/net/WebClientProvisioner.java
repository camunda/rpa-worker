package io.camunda.rpa.worker.net;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class WebClientProvisioner {
	private final ObjectProvider<WebClient.Builder> webClientBuilder;
	
	private final Consumer<WebClient.Builder> defaultClientConfig;
	private final UnaryOperator<HttpClient> defaultConnectorConfig;
	private final Supplier<HttpClient> defaultHttpClientFactory;
	
	@Autowired
	public WebClientProvisioner(ObjectProvider<WebClient.Builder> webClientBuilder) {
		this(webClientBuilder, _ -> {}, c -> c.followRedirect(true), HttpClient::create);
	}
	
	public WebClient webClient(Consumer<WebClient.Builder> clientConfig, UnaryOperator<HttpClient> connectorConfig) {
		return webClientBuilder
				.getObject()
				.apply(defaultClientConfig)
				.clientConnector(new ReactorClientHttpConnector(defaultConnectorConfig
						.andThen(connectorConfig)
						.apply(defaultHttpClientFactory.get())))
				.apply(clientConfig)
				.build();
	}
	
	public WebClient webClient() {
		return webClient(_ -> {});
	}
	
	public WebClient webClient(Consumer<WebClient.Builder> clientConfig) {
		return webClient(clientConfig, UnaryOperator.identity());
	}
}
