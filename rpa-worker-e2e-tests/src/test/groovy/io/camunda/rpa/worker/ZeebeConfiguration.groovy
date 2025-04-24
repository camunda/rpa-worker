package io.camunda.rpa.worker

import org.springframework.mock.env.MockPropertySource

class ZeebeConfiguration {

	private static final ZeebeConfiguration instance = new ZeebeConfiguration()

	static ZeebeConfiguration get() {
		return instance
	}
	
	final Map<String, String> configProperties = [:]

	ZeebeConfiguration() {
		
		Map<String, String> overrides = [:]
		
		if (System.properties['camunda.rpa.e2e.worker.override']) 
			new Properties().tap { p ->
				p.load(getClass().getClassLoader().getResourceAsStream("application-${System.properties['camunda.rpa.e2e.worker.override']}.properties"))
				overrides.putAll(p)
			}

		String camundaHost = overrides['camunda.rpa.e2e.camunda-host']
				?: System.getenv("CAMUNDA_RPA_E2E_CAMUNDAHOST")
				?: "camunda.local"

		String clientSecret = overrides['camunda.rpa.e2e.client-secret']
				?: System.getenv("CAMUNDA_RPA_E2E_CLIENTSECRET")
				?: System.getenv("CAMUNDA_CLIENT_AUTH_CLIENTSECRET")
				?: "unset"

		String operateClient = overrides['camunda.rpa.e2e.operate-client']
				?: System.getenv("CAMUNDA_RPA_E2E_OPERATECLIENT")
				?: "e2e"
		
		String operateSecret = overrides['camunda.rpa.e2e.operate-client-secret']
				?: System.getenv("CAMUNDA_RPA_E2E_OPERATECLIENTSECRET")
				?: "e2e-client-secret"

		configProperties['json.logging.enabled'] = 'false'
		configProperties["camunda.client.mode"] = "selfmanaged"
		configProperties["camunda.client.auth.client-id"] = "zeebe"
		configProperties["camunda.client.auth.client-secret"] = clientSecret
		configProperties["camunda.client.auth.operate-secret"] = operateSecret
		configProperties["camunda.client.zeebe.rest-address"] = "http://zeebe.${camundaHost}"
		configProperties["camunda.client.zeebe.grpc-address"] = "http://zeebe.${camundaHost}"
		configProperties["camunda.client.identity.base-url"] = "http://${camundaHost}/auth/"
		configProperties["camunda.client.auth.issuer"] = "http://${camundaHost}/auth/realms/camunda-platform/protocol/openid-connect/token"
		configProperties["camunda.rpa.zeebe.auth-endpoint"] = "http://${camundaHost}/auth/realms/camunda-platform/protocol/openid-connect"
		configProperties["camunda.client.zeebe.base-url"] = 'http://localhost:8080/zeebe'
		configProperties["camunda.client.zeebe.audience"] = "zeebe.${camundaHost}"
		configProperties["camunda.rpa.e2e.camunda-host"] = camundaHost
		configProperties["camunda.rpa.e2e.operate-client"] = operateClient
		configProperties["camunda.rpa.e2e.operate-client-secret"] = operateSecret
		configProperties["logging.level.io.camunda.zeebe.client.impl.ZeebeCallCredentials"] = "OFF"
		configProperties['camunda.rpa.python-runtime.type'] = "python"
		
		configProperties.putAll(overrides)
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
