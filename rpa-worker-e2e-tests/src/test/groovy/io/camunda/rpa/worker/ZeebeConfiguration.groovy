package io.camunda.rpa.worker

import groovy.util.logging.Slf4j
import org.springframework.mock.env.MockPropertySource

@Slf4j
class ZeebeConfiguration {

	private static final ZeebeConfiguration instance = new ZeebeConfiguration()

	static ZeebeConfiguration get() {
		return instance
	}
	
	final Map<String, String> configProperties = [:]

	ZeebeConfiguration() {
		
		Map<String, String> overrides = [:]

		if (System.getenv("CAMUNDA_VERSION"))
			new Properties().tap { p ->
				p.load(getClass().getClassLoader().getResourceAsStream("application-${System.getenv("CAMUNDA_VERSION")}e2e.properties"))
				overrides.putAll(p)
			}

		if (System.properties['camunda.rpa.e2e.worker.override']) 
			new Properties().tap { p ->
				p.load(getClass().getClassLoader().getResourceAsStream("application-${System.properties['camunda.rpa.e2e.worker.override']}.properties"))
				overrides.putAll(p)
			}

		String clientId = System.getenv("CAMUNDA_CLIENT_AUTH_CLIENTID") ?: "zeebe"
		
		String clientSecret = System.getenv("CAMUNDA_CLIENT_AUTH_CLIENTSECRET") ?: "unset"

		String operateClient = overrides['camunda.rpa.e2e.operate-client']
				?: System.getenv("CAMUNDA_RPA_E2E_OPERATECLIENT")
				?: "e2e"
		
		String operateSecret = overrides['camunda.rpa.e2e.operate-client-secret']
				?: System.getenv("CAMUNDA_RPA_E2E_OPERATECLIENTSECRET")
				?: "e2e-client-secret"

		configProperties['json.logging.enabled'] = 'false'

		if(overrides['camunda.rpa.zeebe.auth-method'] != "cookie") {
//			configProperties["camunda.client.mode"] = "selfmanaged"
			configProperties["camunda.client.auth.client-id"] = clientId
			configProperties["camunda.client.auth.client-secret"] = clientSecret
		}
		
		configProperties["logging.level.io.camunda.zeebe.client.impl.ZeebeCallCredentials"] = "OFF"
		configProperties["logging.level.io.camunda.client.impl.CamundaCallCredentials"] = "OFF"
		
		configProperties['camunda.rpa.python-runtime.type'] = "python"
		
		configProperties["camunda.rpa.e2e.operate-client"] = operateClient
		configProperties["camunda.rpa.e2e.operate-client-secret"] = operateSecret
		
		configProperties.putAll(overrides)
		
		int four = 2 + 2
	}

	Map<String, String> getEnvironment() {
		return configProperties.collectEntries { k, v ->
			[
					k.replaceAll('\\.', '_').replaceAll('-', '').toUpperCase(),
					v.toString()
			]
		}
	}
	
	MockPropertySource installProperties(MockPropertySource propertySource) {
		configProperties.each { k, v ->
			println "${k} = ${v}"
			if( ! k || ! v) return
			propertySource.withProperty(k, v)
		}
		return propertySource
	}

	String getEnv(String name) {
		if(configProperties.containsKey(name))
			return configProperties[name]
		
		return System.getenv(name)
	}
}
