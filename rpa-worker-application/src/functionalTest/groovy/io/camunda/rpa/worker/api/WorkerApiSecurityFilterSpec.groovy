package io.camunda.rpa.worker.api

import io.camunda.client.api.response.ActivatedJob
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.workspace.Workspace
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.file.Path

import static io.camunda.rpa.worker.zeebe.ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY

class WorkerApiSecurityFilterSpec extends Specification implements PublisherUtils {

	static final String API_TOKEN = "test-api-token-of-at-least-32-characters"
	CallbackTokenService callbacks = new CallbackTokenService()
	WorkerApiSecurityFilter filter = new WorkerApiSecurityFilter(new ApiSecurityProperties(API_TOKEN, ["worker.example:443"]), callbacks)
	boolean passed
	WebFilterChain chain = { passed = true; Mono.empty() }

	void "Rejects untrusted, missing, duplicate, malformed and wrong-port Host authorities"() {
		given:
		def request = MockServerHttpRequest.post("/script/evaluate")
				.localAddress(new InetSocketAddress("127.0.0.1", 36227))
				.header(HttpHeaders.AUTHORIZATION, "Bearer $API_TOKEN")
		if (hosts)
			request.header(HttpHeaders.HOST, hosts as String[])
		def exchange = MockServerWebExchange.from(request)

		when:
		block filter.filter(exchange, chain)

		then:
		!passed
		exchange.response.statusCode == HttpStatus.FORBIDDEN

		where:
		hosts << [[], ["rebind.example:36227"], ["localhost:36228"], ["localhost"],
				  ["localhost:36227", "localhost:36227"], ["localhost:36227,rebind.example"],
				  ["localhost.:36227"], ["localhost:36227@rebind.example"], ["[::1]:36228"]]
	}

	void "Accepts loopback and explicitly configured Host authorities"() {
		when:
		def exchange = request("/script/evaluate", API_TOKEN, host)
		block filter.filter(exchange, chain)

		then:
		passed

		where:
		host << ["localhost:36227", "LOCALHOST:36227", "127.0.0.1:36227", "[::1]:36227", "worker.example:443"]
	}

	void "Evaluation requires an administrative bearer token"() {
		when:
		def exchange = request("/script/evaluate", token)
		block filter.filter(exchange, chain)

		then:
		!passed
		exchange.response.statusCode == HttpStatus.UNAUTHORIZED
		exchange.response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE)

		where:
		token << [null, "", "wrong"]
	}

	void "Evaluation remains closed without a configured token"() {
		given:
		filter = new WorkerApiSecurityFilter(new ApiSecurityProperties("", []), callbacks)
		def exchange = request("/script/evaluate", "")

		when:
		block filter.filter(exchange, chain)

		then:
		!passed
		exchange.response.statusCode == HttpStatus.UNAUTHORIZED
	}

	void "Health probes accept pod authorities without API credentials"() {
		given:
		def exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
				.localAddress(new InetSocketAddress("127.0.0.1", 36227))
				.header(HttpHeaders.HOST, "10.42.0.12:36227"))

		when:
		block filter.filter(exchange, chain)

		then:
		passed == allowed
		if (!allowed)
			assert exchange.response.statusCode == HttpStatus.FORBIDDEN

		where:
		method                                  | path                         | allowed
		org.springframework.http.HttpMethod.GET  | "/actuator/health"           | true
		org.springframework.http.HttpMethod.GET  | "/actuator/health/liveness"  | true
		org.springframework.http.HttpMethod.GET  | "/actuator/health/readiness" | true
		org.springframework.http.HttpMethod.HEAD | "/actuator/health/readiness" | true
		org.springframework.http.HttpMethod.POST | "/actuator/health"           | false
		org.springframework.http.HttpMethod.GET  | "/actuator/health-extra"     | false
		org.springframework.http.HttpMethod.GET  | "/actuator/prometheus"       | false
		org.springframework.http.HttpMethod.GET  | "/actuator/metrics"          | false
		org.springframework.http.HttpMethod.GET  | "/api/authentication"        | false
	}

	void "Monitoring requires both a trusted Host and API credentials"() {
		given:
		def builder = MockServerHttpRequest.get(path)
				.localAddress(new InetSocketAddress("127.0.0.1", 36227))
				.header(HttpHeaders.HOST, host)
		if (token)
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
		def exchange = MockServerWebExchange.from(builder)

		when:
		block filter.filter(exchange, chain)

		then:
		passed == allowed
		if (!allowed)
			assert exchange.response.statusCode == status

		where:
		path                         | host                    | token     | allowed | status
		"/actuator/prometheus"        | "localhost:36227"       | null      | false   | HttpStatus.UNAUTHORIZED
		"/actuator/metrics"           | "localhost:36227"       | null      | false   | HttpStatus.UNAUTHORIZED
		"/actuator/metrics/jvm.info"  | "localhost:36227"       | API_TOKEN | true    | null
		"/actuator/prometheus"        | "worker.example:443"    | API_TOKEN | true    | null
		"/actuator/prometheus"        | "10.42.0.12:36227"      | API_TOKEN | false   | HttpStatus.FORBIDDEN
	}

	void "Execution tokens cannot evaluate scripts or cross workspace and job boundaries"() {
		given:
		ActivatedJob job = Stub() { getKey() >> 123L }
		Workspace workspace = new Workspace("one", Path.of("/one"), [(ZEEBE_JOB_WORKSPACE_PROPERTY): job], null)
		callbacks.openExecution(workspace)
		String token = block(callbacks.getEnvironmentVariables(workspace, null)).RPA_CALLBACK_TOKEN
		def exchange = request(path, useAdmin ? API_TOKEN : token)

		when:
		block filter.filter(exchange, chain)

		then:
		passed == allowed
		if (!allowed)
			assert exchange.response.statusCode == HttpStatus.UNAUTHORIZED

		where:
		path                           | useAdmin | allowed
		"/workspace/one/variables"     | false    | true
		"/file/store/one"              | false    | true
		"/file/retrieve/one"           | false    | true
		"/zeebe/job/123/throw"         | false    | true
		"/workspace/two/variables"     | false    | false
		"/file/store/two"              | false    | false
		"/file/retrieve/two"           | false    | false
		"/zeebe/job/124/throw"         | false    | false
		"/zeebe/job/not-a-key/throw"   | false    | false
		"/script/evaluate"            | false    | false
		"/zeebe/job/123/throw"         | true     | false
		"/workspace/one/variables"    | true     | false
	}

	void "Revocation rejects callbacks while retained workspace files remain readable"() {
		given:
		Workspace workspace = new Workspace("one", Path.of("/one"))
		callbacks.openExecution(workspace)
		String token = block(callbacks.getEnvironmentVariables(workspace, null)).RPA_CALLBACK_TOKEN
		callbacks.afterRobotExecution(workspace)
		def exchange = request("/workspace/one/variables", token)

		when:
		block filter.filter(exchange, chain)

		then:
		!passed
		exchange.response.statusCode == HttpStatus.UNAUTHORIZED

		when:
		def file = MockServerWebExchange.from(MockServerHttpRequest.get("/workspace/one/output/main/log.html")
				.localAddress(new InetSocketAddress("127.0.0.1", 36227))
				.header(HttpHeaders.HOST, "localhost:36227"))
		block filter.filter(file, chain)

		then:
		passed
	}

	private static MockServerWebExchange request(String path, String token, String host = "localhost:36227") {
		def request = MockServerHttpRequest.post(path)
				.localAddress(new InetSocketAddress("127.0.0.1", 36227))
				.header(HttpHeaders.HOST, host)
		if (token != null)
			request.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
		return MockServerWebExchange.from(request)
	}
}
