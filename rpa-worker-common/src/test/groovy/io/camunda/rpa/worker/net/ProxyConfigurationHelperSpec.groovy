package io.camunda.rpa.worker.net

import io.camunda.rpa.worker.net.ProxyProperties.ConfigMode
import io.camunda.rpa.worker.net.ProxyProperties.PreferredSource
import org.apache.commons.lang3.SystemProperties
import spock.lang.Specification
import spock.lang.Subject
import spock.util.environment.RestoreSystemProperties

class ProxyConfigurationHelperSpec extends Specification {

	Properties systemPropertySink = new Properties()

	void "ConfigMode of None sets no system properties and has no env vars to forward"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.None),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	@RestoreSystemProperties
	void "ConfigMode of AllOrNothing sets no system properties and has no env vars to forward when both proxy configurations are present"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[HTTP_PROXY: 'http://proxy-host:8080'])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	void "Neither active ConfigMode sets system properties and has no env vars to forward when preferred source has no config"(ConfigMode configMode, PreferredSource preferredSource) {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(configMode, preferredSource),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()

		where:
		configMode              | preferredSource
		ConfigMode.AllOrNothing | PreferredSource.Java
		ConfigMode.AllOrNothing | PreferredSource.Generic
		ConfigMode.Merge        | PreferredSource.Java
		ConfigMode.Merge        | PreferredSource.Generic
	}

	void "ConfigMode of AllOrNothing sets all Java properties from the generic configuration"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						HTTP_PROXY: 'http://proxy-host:8080',
						HTTPS_PROXY: 'https://proxy-host:8383',
						NO_PROXY: "localhost,other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "8080"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "8383"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|other-host"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	@RestoreSystemProperties
	void "ConfigMode of AllOrNothing sets env vars from the Java configuration"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"
		System.properties[SystemProperties.HTTPS_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTPS_PROXY_PORT] = "8383"
		System.properties[SystemProperties.HTTP_NON_PROXY_HOSTS] = "localhost|other-host"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing, PreferredSource.Java),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://proxy-host:8080",
				HTTPS_PROXY: "https://proxy-host:8383",
				NO_PROXY: "localhost,other-host"
		]
	}

	@RestoreSystemProperties
	void "ConfigMode of Merge sets all Java properties from the merged Java/generic configuration"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"
		System.properties[SystemProperties.HTTP_NON_PROXY_HOSTS] = "localhost"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.Merge),
				systemPropertySink,
				[
						HTTPS_PROXY: 'https://proxy-host:8383',
						NO_PROXY   : "other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "8080"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "8383"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|other-host"

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://proxy-host:8080",
				HTTPS_PROXY: "https://proxy-host:8383",
				NO_PROXY: "localhost,other-host"
		]
	}

	@RestoreSystemProperties
	void "ConfigMode of Merge sets all env vars from the merged Java/generic configuration"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"
		System.properties[SystemProperties.HTTP_NON_PROXY_HOSTS] = "localhost"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.Merge, PreferredSource.Java),
				systemPropertySink,
				[
						HTTPS_PROXY: 'https://proxy-host:8383',
						NO_PROXY   : "other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "8080"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "8383"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "other-host|localhost"

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://proxy-host:8080",
				HTTPS_PROXY: "https://proxy-host:8383",
				NO_PROXY: "other-host,localhost"
		]
	}

	void "ConfigMode of Merge sets all Java properties from the generic configuration when no existing properties"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.Merge),
				systemPropertySink,
				[
						HTTP_PROXY: 'http://proxy-host:8080',
						HTTPS_PROXY: 'https://proxy-host:8383',
						NO_PROXY: "localhost,other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "8080"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "8383"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|other-host"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	@RestoreSystemProperties
	void "ConfigMode of Merge sets all env vars from the Java configuration when there are no existing env vars"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"
		System.properties[SystemProperties.HTTPS_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTPS_PROXY_PORT] = "8383"
		System.properties[SystemProperties.HTTP_NON_PROXY_HOSTS] = "localhost|other-host"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.Merge, PreferredSource.Java),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://proxy-host:8080",
				HTTPS_PROXY: "https://proxy-host:8383",
				NO_PROXY: "localhost,other-host"
		]
	}

	void "Aliases older-style env var names"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						http_proxy: 'http://proxy-host:8080',
						https_proxy: 'https://proxy-host:8383',
						NO_PROXY: "localhost,other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "8080"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "8383"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|other-host"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	void "Prefers new env var names when both old and new are present"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						http_proxy: 'http://should-be-ignored:8080',
						HTTP_PROXY: 'https://should-be-used:8080',
						https_proxy: 'http://should-be-ignored:8080',
						HTTPS_PROXY: 'https://should-be-used:8080',
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "should-be-used"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "should-be-used"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	void "Uses default port values when forced"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						HTTP_PROXY: 'http://proxy-host',
						HTTPS_PROXY: 'https://proxy-host',
						NO_PROXY: "localhost,other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "80"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "443"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|other-host"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	void "Ignores invalid env var values"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						HTTP_PROXY: 'http://proxy-host:8080',
						HTTPS_PROXY: 'https://',
						NO_PROXY: "localhost,other-host"
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST]
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT]
		! systemPropertySink[SystemProperties.HTTPS_PROXY_HOST]
		! systemPropertySink[SystemProperties.HTTPS_PROXY_PORT]
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|other-host"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	@RestoreSystemProperties
	void "Implicit Java proxy config is ignored"() {
		setup:
		System.properties[SystemProperties.JAVA_NET_USE_SYSTEM_PROXIES] = "true"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}

	void "Copies ALL_PROXY into both scheme proxies when all that is provided"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[ALL_PROXY: "http://proxy-host:8080"])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_PROXY_PORT] == "8080"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTPS_PROXY_PORT] == "8080"

		and:
		proxyConfigurationHelper.getForwardEnv().isEmpty()
	}
	
	@RestoreSystemProperties
	void "Handles IPv6 coming in from Java properties"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "::1"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"
		System.properties[SystemProperties.HTTPS_PROXY_HOST] = "0:0:0:0:0:0:0:1"
		System.properties[SystemProperties.HTTPS_PROXY_PORT] = "8383"
		System.properties[SystemProperties.HTTP_NON_PROXY_HOSTS] = "localhost|other-host"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing, PreferredSource.Java),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://[::1]:8080",
				HTTPS_PROXY: "https://[0:0:0:0:0:0:0:1]:8383",
				NO_PROXY: "localhost,other-host"
		]
	}
	
	void "Handles IPv6 coming in from generic config"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						HTTP_PROXY: 'http://[::1]:8080',
						HTTPS_PROXY: 'https://[0:0:0:0:0:0:0:1]:8383'
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "::1"
		systemPropertySink[SystemProperties.HTTPS_PROXY_HOST] == "0:0:0:0:0:0:0:1"
	}
	
	@RestoreSystemProperties
	void "Ignores invalid port values coming in from Java properties"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "foo"
		System.properties[SystemProperties.HTTPS_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTPS_PROXY_PORT] = "bar"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing, PreferredSource.Java),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://proxy-host:80",
				HTTPS_PROXY: "https://proxy-host:443",
		]
	}
	
	@RestoreSystemProperties
	void "Handles wildcards coming in from Java properties"() {
		setup:
		System.properties[SystemProperties.HTTP_PROXY_HOST] = "proxy-host"
		System.properties[SystemProperties.HTTP_PROXY_PORT] = "8080"
		System.properties[SystemProperties.HTTP_NON_PROXY_HOSTS] = "localhost|*.internal.domain"

		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing, PreferredSource.Java),
				systemPropertySink,
				[:])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink.isEmpty()

		and:
		proxyConfigurationHelper.getForwardEnv() == [
				HTTP_PROXY: "http://proxy-host:8080",
				NO_PROXY: "localhost,.internal.domain"
		]
	}
	
	void "Handles wildcards coming in from generic config"() {
		@Subject ProxyConfigurationHelper proxyConfigurationHelper = new ProxyConfigurationHelper(
				proxyProperties(ConfigMode.AllOrNothing),
				systemPropertySink,
				[
						HTTP_PROXY: 'http://proxy-host:8080',
						NO_PROXY: 'localhost,.internal.domain'
				])

		when:
		proxyConfigurationHelper.installSystemProperties()

		then:
		systemPropertySink[SystemProperties.HTTP_PROXY_HOST] == "proxy-host"
		systemPropertySink[SystemProperties.HTTP_NON_PROXY_HOSTS] == "localhost|*.internal.domain"
	}


	private static proxyProperties(ConfigMode configMode, PreferredSource preferredSource = PreferredSource.Generic) {
		return new ProxyProperties(configMode, preferredSource)
	}
}
