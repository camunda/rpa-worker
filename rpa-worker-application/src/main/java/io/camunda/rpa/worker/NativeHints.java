package io.camunda.rpa.worker;

import io.grpc.ProxyDetector;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.proxy.ProxyConnectionEvent;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import org.htmlunit.javascript.host.html.HTMLInputElement;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import reactor.netty.http.server.ProxyProtocolSupportType;
import reactor.netty.tcp.AbstractProtocolSslContextSpec;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
						TypeReference.of(SSLEngine.class),
						TypeReference.of(SSLEngineResult.class),
						TypeReference.of(SSLEngineResult.HandshakeStatus.class),
						TypeReference.of(SSLEngineResult.Status.class),
						TypeReference.of(SSLException.class),
						TypeReference.of(SSLHandshakeException.class),
						TypeReference.of(SSLSession.class),
						TypeReference.of(CertificateException.class),
						TypeReference.of("io.netty.handler.ssl.SslUtils"),
						TypeReference.of(KeyManagementException.class),
						TypeReference.of(NoSuchAlgorithmException.class),
						TypeReference.of(NoSuchProviderException.class),
						TypeReference.of(Provider.class),
						TypeReference.of(TrustManager.class),
						TypeReference.of(SNIServerName.class),
						TypeReference.of(SSLEngine.class),
						TypeReference.of(SSLException.class),

						TypeReference.of(SslContext.class),
						TypeReference.of(SslContextBuilder.class),
						TypeReference.of(SslHandler.class),
						TypeReference.of(SslHandshakeCompletionEvent.class),

						TypeReference.of(Http2SecurityUtil.class),
						TypeReference.of(ApplicationProtocolConfig.class),
						TypeReference.of(ApplicationProtocolNames.class),
						TypeReference.of(SslContext.class),
						TypeReference.of(SslContextBuilder.class),
						TypeReference.of(SslProvider.class),
						TypeReference.of(SupportedCipherSuiteFilter.class),
						TypeReference.of(AbstractProtocolSslContextSpec.class),

						TypeReference.of(KeyManager.class),
						TypeReference.of(KeyManagerFactory.class),
						TypeReference.of(PrivateKey.class),
						TypeReference.of(X509Certificate.class),

//						TypeReference.of(JobWorkerValueCustomizerCompat.class),
						TypeReference.of(io.camunda.client.impl.response.ActivatedJobImpl.class),
						TypeReference.of(io.camunda.zeebe.client.impl.response.ActivatedJobImpl.class),
						TypeReference.of(io.camunda.client.api.ProblemDetail.class),
						TypeReference.of(io.camunda.client.protocol.rest.ProblemDetail.class),
						TypeReference.of(io.camunda.zeebe.client.protocol.rest.ProblemDetail.class),
						TypeReference.of(io.camunda.client.protocol.rest.JobErrorRequest.class),
						TypeReference.of(io.camunda.zeebe.client.protocol.rest.JobErrorRequest.class),
						TypeReference.of(io.camunda.client.protocol.rest.TopologyResponse.class),
						TypeReference.of(io.camunda.zeebe.client.protocol.rest.TopologyResponse.class),
						TypeReference.of(io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse.class),

						TypeReference.of(HTMLInputElement.class))

				.forEach(klass -> hints.reflection().registerType(klass, MemberCategory.values()));
	}
}
