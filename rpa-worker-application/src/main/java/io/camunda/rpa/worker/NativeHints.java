package io.camunda.rpa.worker;

import io.grpc.ProxyDetector;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import reactor.netty.http.server.ProxyProtocolSupportType;
import reactor.netty.transport.ProxyProvider;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.stream.Stream;


@Configuration
@ImportRuntimeHints(NativeHints.class)
class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		hints.resources().registerPattern("runtime.zip");

		Stream.of(
						TypeReference.of(Proxy.class),
						TypeReference.of(ProxySelector.class),
						TypeReference.of(ProxyDetector.class),
						TypeReference.of("io.grpc.internal.ProxyDetectorImpl"),
						TypeReference.of(Authenticator.class),
						TypeReference.of(PasswordAuthentication.class),
						TypeReference.of(ProxyProvider.class),
						TypeReference.of("io.grpc.netty.ProtocolNegotiators"),
						TypeReference.of(ProxyConnectException.class),
						TypeReference.of(ProxyConnectionEvent.class),
						TypeReference.of(Socks4ProxyHandler.class),
						TypeReference.of(Socks5ProxyHandler.class),
						TypeReference.of(HttpProxyHandler.class),
						TypeReference.of(ProxyHandler.class),
						TypeReference.of(ProxyProtocolSupportType.class))
				
				.forEach(klass -> hints.reflection().registerType(klass, MemberCategory.values()));
	}
}
