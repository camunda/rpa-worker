package io.camunda.rpa.worker.zeebe

import io.camunda.client.spring.properties.CamundaClientAuthProperties
import io.camunda.client.spring.properties.CamundaClientProperties
import io.camunda.rpa.worker.zeebe.ZeebeProperties.AuthMethod
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import spock.lang.Specification

class OidcConfigurationHelperSpec extends Specification {
	
	private static final URI NOOP_URL = "http://none".toURI()

	WebClient webClient = Mock()
	
	void "Returns no-op URL when both auth methods are not token/oidc"(AuthMethod rpaAuthMethod, CamundaClientAuthProperties.AuthMethod camundaAuthMethod) {
		when:
		URI r = OidcConfigurationHelper.getTokenUrl(
				zeebeProperties(rpaAuthMethod),
				camundaProperties(camundaAuthMethod),
				webClient)
		
		then:
		r == NOOP_URL
		
		and:
		0 * webClient._

		where:
		rpaAuthMethod     | camundaAuthMethod
		AuthMethod.TOKEN  | CamundaClientAuthProperties.AuthMethod.basic
		AuthMethod.TOKEN  | CamundaClientAuthProperties.AuthMethod.none
		AuthMethod.BASIC  | CamundaClientAuthProperties.AuthMethod.oidc
		AuthMethod.COOKIE | CamundaClientAuthProperties.AuthMethod.oidc
		AuthMethod.NONE   | CamundaClientAuthProperties.AuthMethod.oidc
	}
	
	void "Returns token URL if provided explicitly"() {
		given:
		URI tokenUrl = "http://explicit-token-url".toURI()
		
		when:
		URI r = OidcConfigurationHelper.getTokenUrl(
				zeebeProperties(AuthMethod.TOKEN),
				camundaProperties(CamundaClientAuthProperties.AuthMethod.oidc, tokenUrl),
				webClient)

		then:
		r == tokenUrl

		and:
		0 * webClient._
	}
	
	void "Retrieves token URL from well known config if have issuer URL"() {
		given:
		URI issuerUrl = "http://issuer-url/".toURI()
		URI tokenUrl = "http://token-url/".toURI()

		when:
		URI r = OidcConfigurationHelper.getTokenUrl(
				zeebeProperties(AuthMethod.TOKEN),
				camundaProperties(CamundaClientAuthProperties.AuthMethod.oidc, null, issuerUrl),
				webClient)

		then:
		1 * webClient.get() >> Mock(WebClient.RequestHeadersUriSpec) {
			1 * uri(issuerUrl.resolve(".well-known/openid-configuration")) >> Mock(WebClient.RequestHeadersSpec) {
				1 * retrieve() >> Mock(WebClient.ResponseSpec) {
					1 * bodyToMono(OidcConfigurationHelper.WellKnownConfiguration.class) >> Mono.just(new OidcConfigurationHelper.WellKnownConfiguration(tokenUrl))
				}
			}
		}
		
		and:
		r == tokenUrl
	}
	
	private static ZeebeProperties zeebeProperties(AuthMethod authMethod) {
		return new ZeebeProperties(null, null, 0, authMethod)
	}
	
	private CamundaClientProperties camundaProperties(CamundaClientAuthProperties.AuthMethod authMethod, URI tokenUrl = null, URI issuerUrl = null) {
		return Stub(CamundaClientProperties) {
			getAuth() >> Stub(CamundaClientAuthProperties) {
				getMethod() >> authMethod
				getTokenUrl() >> tokenUrl
				getIssuerUrl() >> issuerUrl
			}
		}
	}
}
