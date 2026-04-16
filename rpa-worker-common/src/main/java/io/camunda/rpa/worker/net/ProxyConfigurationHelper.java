package io.camunda.rpa.worker.net;

import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.SystemProperties.HTTPS_PROXY_HOST;
import static org.apache.commons.lang3.SystemProperties.HTTPS_PROXY_PORT;
import static org.apache.commons.lang3.SystemProperties.HTTP_NON_PROXY_HOSTS;
import static org.apache.commons.lang3.SystemProperties.HTTP_PROXY_HOST;
import static org.apache.commons.lang3.SystemProperties.HTTP_PROXY_PORT;
import static org.apache.commons.lang3.SystemProperties.JAVA_NET_USE_SYSTEM_PROXIES;

@Component
@Slf4j
class ProxyConfigurationHelper {
	
	private final ProxyProperties proxyProperties;
	private final Properties systemProperties;
	private final Map<String, String> mutableEnv;
	
	@Getter
	private final Map<String, String> forwardEnv;
	private final Map<String, String> systemPropertiesToInstall;

	@Autowired
	public ProxyConfigurationHelper(ProxyProperties proxyProperties) {
		this(proxyProperties, System.getProperties(), System.getenv());
	}
	
	ProxyConfigurationHelper(ProxyProperties proxyProperties, Properties systemProperties, Map<String, String> env) {
		this.proxyProperties = proxyProperties;
		this.systemProperties = systemProperties;
		this.mutableEnv = new HashMap<>(env);
		
		ResolvedProxyConfig resolvedProxyConfig = resolveProxyConfig();
		this.forwardEnv = resolvedProxyConfig.envVarsToForward();
		this.systemPropertiesToInstall = resolvedProxyConfig.systemPropertiesToInstall();
	}

	private ResolvedProxyConfig resolveProxyConfig() {
		if(proxyProperties.configMode() == ProxyProperties.ConfigMode.None)
			return new ResolvedProxyConfig();

		Optional<ProxyConfigBundle> javaExplicitProxyConfiguration = getJavaExplicitProxyConfiguration();
		Optional<ProxyConfigBundle> genericProxyConfiguration = getGenericProxyConfiguration();
		Optional<ProxyConfigBundle> source = proxyProperties.preferredSource() == ProxyProperties.PreferredSource.Java
				? javaExplicitProxyConfiguration
				: genericProxyConfiguration;
		Optional<ProxyConfigBundle> other = proxyProperties.preferredSource() == ProxyProperties.PreferredSource.Java
				? genericProxyConfiguration
				: javaExplicitProxyConfiguration;

		return proxyProperties.configMode() == ProxyProperties.ConfigMode.AllOrNothing 
				? allOrNothing(source, other) 
				: merge(source, other);
	}

	private ResolvedProxyConfig allOrNothing(Optional<ProxyConfigBundle> source, Optional<ProxyConfigBundle> other) {
		if (source.isPresent() && other.isPresent())
			return new ResolvedProxyConfig();

		return source.map(s -> proxyProperties.preferredSource() == ProxyProperties.PreferredSource.Java
						? new ResolvedProxyConfig(Collections.emptyMap(), toEnv(s))
						: new ResolvedProxyConfig(toProperties(s), Collections.emptyMap()))
				.orElseGet(ResolvedProxyConfig::new);
	}

	private ResolvedProxyConfig merge(Optional<ProxyConfigBundle> source, Optional<ProxyConfigBundle> other) {
		if(other.isEmpty())
			return source.map(s -> proxyProperties.preferredSource() == ProxyProperties.PreferredSource.Java
							? new ResolvedProxyConfig(Collections.emptyMap(), toEnv(s))
							: new ResolvedProxyConfig(toProperties(s), Collections.emptyMap()))
					.orElseGet(ResolvedProxyConfig::new);

		ProxyConfigBundle merged = new ProxyConfigBundle(
				source.flatMap(ProxyConfigBundle::httpProxyHost).or(() -> other.flatMap(ProxyConfigBundle::httpProxyHost)),
				source.flatMap(ProxyConfigBundle::httpProxyPort).or(() -> other.flatMap(ProxyConfigBundle::httpProxyPort)),
				Optional.of(Stream.concat(
						other.flatMap(ProxyConfigBundle::nonProxyHosts).stream().flatMap(Arrays::stream),
						source.flatMap(ProxyConfigBundle::nonProxyHosts).stream().flatMap(Arrays::stream)).toArray(String[]::new)),
				source.flatMap(ProxyConfigBundle::httpsProxyHost).or(() -> other.flatMap(ProxyConfigBundle::httpsProxyHost)),
				source.flatMap(ProxyConfigBundle::httpsProxyPort).or(() -> other.flatMap(ProxyConfigBundle::httpsProxyPort)));


		return new ResolvedProxyConfig(toProperties(merged), toEnv(merged));
	}

	private Map<String, String> toProperties(ProxyConfigBundle source) {
		Map<String, String> newSystemProperties = new HashMap<>();
		source.httpProxyHost().ifPresent(s -> newSystemProperties.put(HTTP_PROXY_HOST, s));
		source.httpProxyPort().ifPresent(s -> newSystemProperties.put(HTTP_PROXY_PORT, String.valueOf(s)));
		source.httpsProxyHost().ifPresent(s -> newSystemProperties.put(HTTPS_PROXY_HOST, s));
		source.httpsProxyPort().ifPresent(s -> newSystemProperties.put(HTTPS_PROXY_PORT, String.valueOf(s)));
		source.nonProxyHosts().ifPresent(s -> newSystemProperties.put(HTTP_NON_PROXY_HOSTS,
				Arrays.stream(s).map(ProxyConfigurationHelper::maybeHandleWildcardForProperties).collect(Collectors.joining("|"))));
		return newSystemProperties;
	}

	private Map<String, String> toEnv(ProxyConfigBundle source) {
		Map<String, String> envVars = new HashMap<>();
		source.httpProxyHost().ifPresent(s -> envVars.put("HTTP_PROXY", uri("http", s, source.httpProxyPort(), 80)));
		source.httpsProxyHost().ifPresent(s -> envVars.put("HTTPS_PROXY", uri("https", s, source.httpsProxyPort(), 443)));
		source.nonProxyHosts().ifPresent(s -> envVars.put("NO_PROXY",
				Arrays.stream(s).map(ProxyConfigurationHelper::maybeHandleWildcardForEnvVars).collect(Collectors.joining(","))));
		return envVars;
	}

	private Optional<ProxyConfigBundle> getJavaExplicitProxyConfiguration() {
		if (Optional.ofNullable(System.getProperty(JAVA_NET_USE_SYSTEM_PROXIES))
				.map(String::trim)
				.map(Boolean::valueOf)
				.orElse(false))
			return Optional.empty();

		ProxyConfigBundle proxyConfigBundle = new ProxyConfigBundle(
				Optional.ofNullable(System.getProperty(HTTP_PROXY_HOST)),
				Optional.ofNullable(System.getProperty(HTTP_PROXY_PORT))
						.flatMap(p -> Try.of(() -> Integer.valueOf(p))
								.onFailure(thrown -> log.atWarn()
										.setCause(thrown)
										.kv("rejectedValue", p)
										.arg(HTTP_PROXY_PORT)
										.log("{} has an invalid value which was ignored"))
								.recover(_ -> 80)
								.toJavaOptional()),
				Optional.ofNullable(System.getProperty(HTTP_NON_PROXY_HOSTS)).map(s -> s.split("\\|")),
				Optional.ofNullable(System.getProperty(HTTPS_PROXY_HOST)),
				Optional.ofNullable(System.getProperty(HTTPS_PROXY_PORT))
						.flatMap(p -> Try.of(() -> Integer.valueOf(p))
								.onFailure(thrown -> log.atWarn()
										.setCause(thrown)
										.kv("rejectedValue", p)
										.arg(HTTPS_PROXY_PORT)
										.log("{} has an invalid value which was ignored"))
								.recover(_ -> 443)
								.toJavaOptional()));

		return proxyConfigBundle.hasAnyProxy()
				? Optional.of(proxyConfigBundle)
				: Optional.empty();
	}

	private Optional<ProxyConfigBundle> getGenericProxyConfiguration() {

		if (mutableEnv.containsKey("http_proxy") && ! mutableEnv.containsKey("HTTP_PROXY"))
			mutableEnv.put("HTTP_PROXY", mutableEnv.get("http_proxy"));
		if (mutableEnv.containsKey("https_proxy") && ! mutableEnv.containsKey("HTTPS_PROXY"))
			mutableEnv.put("HTTPS_PROXY", mutableEnv.get("https_proxy"));

		Optional<URI> httpProxyUri = getGenericProxyComponent("HTTP");
		Optional<URI> httpsProxyUri = getGenericProxyComponent("HTTPS");

		ProxyConfigBundle proxyConfigBundle = new ProxyConfigBundle(
				httpProxyUri.map(URI::getHost).map(ProxyConfigurationHelper::normaliseHost),
				httpProxyUri.map(uri -> uri.getPort() != - 1 ? uri.getPort() : 80),
				Optional.ofNullable(mutableEnv.get("NO_PROXY")).map(s -> s.split(",")),
				httpsProxyUri.map(URI::getHost).map(ProxyConfigurationHelper::normaliseHost),
				httpsProxyUri.map(uri -> uri.getPort() != - 1 ? uri.getPort() : 443));

		return proxyConfigBundle.hasAnyProxy()
				? Optional.of(proxyConfigBundle)
				: Optional.empty();
	}

	private Optional<URI> getGenericProxyComponent(String scheme) {
		return Optional.ofNullable(mutableEnv.get("%s_PROXY".formatted(scheme)))
				.or(() -> Optional.ofNullable(mutableEnv.get("ALL_PROXY")))
				.flatMap(s -> Try.of(() -> URI.create(s))
						.onFailure(thrown -> log.atWarn()
								.setCause(thrown)
								.kv("source", "%s_PROXY/ALL_PROXY".formatted(scheme))
								.kv("value", s)
								.log("Environment variable has invalid value, ignoring"))
						.toJavaOptional());
	}

	public void installSystemProperties() {
		systemProperties.putAll(systemPropertiesToInstall);
	}

	private static String uri(String scheme, String host, Optional<Integer> port, int fallbackPort) {
		return Try.of(() -> new URI(
						scheme,
						null,
						host,
						port.orElse(fallbackPort),
						null,
						null,
						null))
				.get()
				.toString();
	}

	private static String normaliseHost(String host) {
		if(host.startsWith("[") && host.endsWith("]"))
			return host.substring(1, host.length() - 1);
		
		return host;
	}
	private static String maybeHandleWildcardForProperties(String input) {
		return input.startsWith(".") ? "*" + input : input;
	}
	
	private static String maybeHandleWildcardForEnvVars(String input) {
		return input.startsWith("*") ? input.substring(1) : input;
	}


	private record ProxyConfigBundle(
			Optional<String> httpProxyHost,
			Optional<Integer> httpProxyPort,
			Optional<String[]> nonProxyHosts,
			Optional<String> httpsProxyHost,
			Optional<Integer> httpsProxyPort) {
		
		boolean hasAnyProxy() {
			return (httpProxyHost.isPresent() && httpProxyPort.isPresent())
					|| (httpsProxyHost.isPresent() && httpsProxyPort.isPresent());
		}
	}

	private record ResolvedProxyConfig(Map<String, String> systemPropertiesToInstall, Map<String, String> envVarsToForward) {
		public ResolvedProxyConfig() {
			this(Collections.emptyMap(), Collections.emptyMap());
		}
	}

}