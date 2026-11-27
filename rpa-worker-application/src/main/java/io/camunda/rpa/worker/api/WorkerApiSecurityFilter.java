package io.camunda.rpa.worker.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
class WorkerApiSecurityFilter implements WebFilter {

	private static final List<PathPattern> WORKSPACE_CALLBACKS = List.of(
			pattern("/workspace/{workspaceId}/variables"),
			pattern("/file/store/{workspaceId}"),
			pattern("/file/retrieve/{workspaceId}"));
	private static final PathPattern JOB_CALLBACK = pattern("/zeebe/job/{jobKey}/throw");
	private static final PathPattern WORKSPACE_FILES = pattern("/workspace/{workspaceId}/**");

	private final ApiSecurityProperties properties;
	private final CallbackTokenService callbackTokens;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		var request = exchange.getRequest();
		var path = request.getPath().pathWithinApplication();
		boolean readOnly = request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.HEAD;

		// Kubernetes probes use the pod's Host authority and cannot supply API credentials.
		if (readOnly && (path.value().equals("/actuator/health") || path.value().startsWith("/actuator/health/")))
			return chain.filter(exchange);

		if (!hasAllowedHost(exchange)) {
			log.atWarn().log("Rejected HTTP request with an untrusted Host authority");
			exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
			return exchange.getResponse().setComplete();
		}

		// Workspace file URLs are existing read-only capabilities, also used by report iframes.
		if (readOnly && (path.value().equals("/rpa-worker.api.yaml")
				|| path.value().equals("/swagger")
				|| path.value().equals("/swagger-ui.html")
				|| path.value().startsWith("/swagger-ui/")
				|| path.value().startsWith("/webjars/")
				|| path.value().equals("/v3/api-docs")
				|| path.value().startsWith("/v3/api-docs/")
				|| WORKSPACE_FILES.matches(path)))
			return chain.filter(exchange);

		String token = bearerToken(exchange);
		for (PathPattern callback : WORKSPACE_CALLBACKS) {
			var match = callback.matchAndExtract(path);
			if (match != null)
				return callbackTokens.authorizesWorkspace(token, match.getUriVariables().get("workspaceId"))
						? chain.filter(exchange) : unauthorized(exchange);
		}

		var job = JOB_CALLBACK.matchAndExtract(path);
		if (job != null) {
			try {
				long jobKey = Long.parseLong(job.getUriVariables().get("jobKey"));
				return callbackTokens.authorizesJob(token, jobKey)
						? chain.filter(exchange) : unauthorized(exchange);
			} catch (NumberFormatException _) {
				return unauthorized(exchange);
			}
		}

		return CallbackTokenService.tokensMatch(properties.token(), token)
				? chain.filter(exchange) : unauthorized(exchange);
	}

	private boolean hasAllowedHost(ServerWebExchange exchange) {
		List<String> hosts = exchange.getRequest().getHeaders().get(HttpHeaders.HOST);
		if (hosts == null || hosts.size() != 1)
			return false;

		String host = hosts.getFirst();
		InetSocketAddress localAddress = exchange.getRequest().getLocalAddress();
		if (localAddress == null)
			return false;

		int port = localAddress.getPort();
		String suffix = ":" + port;
		boolean loopback = List.of("localhost", "127.0.0.1", "[::1]").stream()
				.anyMatch(name -> host.equalsIgnoreCase(name + suffix) || (port == 80 && host.equalsIgnoreCase(name)));
		return loopback || properties.allowedHosts().stream().anyMatch(host::equalsIgnoreCase);
	}

	private static String bearerToken(ServerWebExchange exchange) {
		List<String> headers = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
		if (headers == null || headers.size() != 1)
			return null;
		String header = headers.getFirst();
		return header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7) : null;
	}

	private static Mono<Void> unauthorized(ServerWebExchange exchange) {
		log.atWarn().log("Rejected HTTP request without a valid API or execution credential");
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"rpa-worker\"");
		return exchange.getResponse().setComplete();
	}

	private static PathPattern pattern(String path) {
		return PathPatternParser.defaultInstance.parse(path);
	}
}
