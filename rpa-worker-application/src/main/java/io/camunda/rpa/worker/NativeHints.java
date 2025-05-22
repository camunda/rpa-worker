package io.camunda.rpa.worker;

import io.grpc.ProxyDetector;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.SslHandler;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import reactor.netty.http.server.ProxyProtocolSupportType;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.security.cert.CertificateException;
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
						TypeReference.of(ProxyProtocolSupportType.class),
						
						TypeReference.of("reactor.netty.http.client.HttpClientSecure"),
						TypeReference.of("reactor.netty.http.Http2SslContextSpec"),
						TypeReference.of("reactor.netty.tcp.SslProvider"),
						TypeReference.of("reactor.netty.http.client.HttpClientSecurityUtils"),
						TypeReference.of("reactor.netty.http.internal.Http3"),
						TypeReference.of(SSLEngine.class),
						TypeReference.of(SSLParameters.class),
						TypeReference.of(SslHandler.class),
						TypeReference.of(javax.net.ssl.SSLEngine.class),
						TypeReference.of(javax.net.ssl.SSLEngineResult.class),
						TypeReference.of(javax.net.ssl.SSLEngineResult.HandshakeStatus.class),
						TypeReference.of(javax.net.ssl.SSLEngineResult.Status.class),
						TypeReference.of(javax.net.ssl.SSLException.class),
						TypeReference.of(javax.net.ssl.SSLHandshakeException.class),
						TypeReference.of(javax.net.ssl.SSLSession.class),
						TypeReference.of(CertificateException.class),
						TypeReference.of("io.netty.handler.ssl.SslUtils"),
						TypeReference.of(java.security.KeyManagementException.class),
						TypeReference.of(java.security.NoSuchAlgorithmException.class),
						TypeReference.of(java.security.NoSuchProviderException.class),
						TypeReference.of(java.security.Provider.class),
						TypeReference.of(TrustManager.class),
						TypeReference.of(javax.net.ssl.SNIServerName.class),
						TypeReference.of(javax.net.ssl.SSLEngine.class),
						TypeReference.of(javax.net.ssl.SSLException.class),

						TypeReference.of(io.netty.handler.ssl.SslContext.class),
						TypeReference.of(io.netty.handler.ssl.SslContextBuilder.class),
						TypeReference.of(io.netty.handler.ssl.SslHandler.class),
						TypeReference.of(io.netty.handler.ssl.SslHandshakeCompletionEvent.class),

						TypeReference.of(io.netty.handler.codec.http2.Http2SecurityUtil.class),
						TypeReference.of(io.netty.handler.ssl.ApplicationProtocolConfig.class),
						TypeReference.of(io.netty.handler.ssl.ApplicationProtocolNames.class),
						TypeReference.of(io.netty.handler.ssl.SslContext.class),
						TypeReference.of(io.netty.handler.ssl.SslContextBuilder.class),
						TypeReference.of(io.netty.handler.ssl.SslProvider.class),
						TypeReference.of(io.netty.handler.ssl.SupportedCipherSuiteFilter.class),
						TypeReference.of(reactor.netty.tcp.AbstractProtocolSslContextSpec.class),

						TypeReference.of(javax.net.ssl.KeyManager.class),
						TypeReference.of(javax.net.ssl.KeyManagerFactory.class),
						TypeReference.of(java.security.PrivateKey.class),
						TypeReference.of(java.security.cert.X509Certificate.class))

				.forEach(klass -> hints.reflection().registerType(klass, MemberCategory.values()));
	}
}
