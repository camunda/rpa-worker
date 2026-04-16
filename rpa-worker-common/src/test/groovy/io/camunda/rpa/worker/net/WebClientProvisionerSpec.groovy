package io.camunda.rpa.worker.net

import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.function.UnaryOperator

class WebClientProvisionerSpec extends Specification {

	WebClient.Builder webClientBuilder = Mock() {
		apply(_) >> { Consumer<WebClient.Builder> bc -> bc.accept(it); return it }
	}
	ObjectProvider<WebClient.Builder> webClientBuilderProvider = Stub() {
		getObject() >> { webClientBuilder }
	}
	Consumer<WebClient.Builder> defaultClientConfig = Mock()
	UnaryOperator<HttpClient> defaultConnectorConfig = Mock() {
		andThen(_) >> { Function after -> { t -> after.apply(it.apply(t)) }}
	}
	HttpClient httpClient = Mock()
	Supplier<HttpClient> httpClientFactory = { httpClient }
	
	ProxyConfigurationHelper proxyConfigurationHelper = Mock()

	@Subject
	WebClientProvisioner webClientProvisioner = new WebClientProvisioner(webClientBuilderProvider, proxyConfigurationHelper, defaultClientConfig, defaultConnectorConfig, httpClientFactory)
	
	WebClient expected = Stub()

	@SuppressWarnings('GroovyAccessibility')
	void "Applies configuration to client/connector and returns correctly constructed WebClient"() {
		given:
		Consumer<WebClient.Builder> clientConfig = Mock()
		UnaryOperator<HttpClient> connectorConfig = Mock()
		
		and:
		HttpClient httpClientAfterDefaultApplied = Mock()
		HttpClient httpClientAfterConfigApplied = Mock()
		
		when:
		WebClient r = webClientProvisioner.webClient(clientConfig, connectorConfig)
		
		then: "Default client config is applied"
		1 * defaultClientConfig.accept(webClientBuilder)
		
		then: "Default connector config is applied"
		1 * defaultConnectorConfig.apply(httpClient) >> httpClientAfterDefaultApplied
		
		then: "Supplied connector config is applied"
		1 * connectorConfig.apply(httpClientAfterDefaultApplied) >> httpClientAfterConfigApplied
		
		then: "Connector is attached to client"
		1 * webClientBuilder.clientConnector(_ as ReactorClientHttpConnector) >> { ReactorClientHttpConnector cc ->
			verifyAll {
				cc.@httpClient == httpClientAfterConfigApplied
			}
			return webClientBuilder
		}
		
		then: "Supplied client config is applied"
		1 * clientConfig.accept(webClientBuilder) 
		
		then: 
		1 * webClientBuilder.build() >> expected
		
		and:
		r == expected
	}
	
	@RestoreSystemProperties
	void "Installs the System Properties from the ProxyConfigurationHelper on init"() {
		when:
		webClientProvisioner.init()
		
		then:
		1 * proxyConfigurationHelper.installSystemProperties()
	}
}
