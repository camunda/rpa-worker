package io.camunda.rpa.worker.net

import io.camunda.rpa.worker.PublisherUtils
import spock.lang.Specification
import spock.lang.Subject

class ProxyEnvironmentVariablesContributorSpec extends Specification implements PublisherUtils {
	
	ProxyConfigurationHelper proxyConfigurationHelper = Stub()
	
	@Subject
	ProxyEnvironmentVariablesContributor contributor = new ProxyEnvironmentVariablesContributor(proxyConfigurationHelper)
	
	void "Returns all variables from the ProxyConfigurationHelper"() {
		given:
		proxyConfigurationHelper.getForwardEnv() >> [FOO: 'bar']

		when:
		Map<String, String> r = block contributor.getEnvironmentVariables(null, null)
		
		then:
		r == [FOO: 'bar']
	}
}
