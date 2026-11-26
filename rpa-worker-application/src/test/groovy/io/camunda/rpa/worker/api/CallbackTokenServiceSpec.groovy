package io.camunda.rpa.worker.api

import io.camunda.client.api.response.ActivatedJob
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.workspace.Workspace
import spock.lang.Specification

import java.nio.file.Path

import static io.camunda.rpa.worker.zeebe.ZeebeJobService.ZEEBE_JOB_WORKSPACE_PROPERTY

class CallbackTokenServiceSpec extends Specification implements PublisherUtils {

	CallbackTokenService service = new CallbackTokenService()

	void "Capabilities are unique, workspace and job scoped, and revoked after execution"() {
		given:
		ActivatedJob job = Stub() { getKey() >> 123L }
		Workspace production = new Workspace("production", Path.of("/production"), [(ZEEBE_JOB_WORKSPACE_PROPERTY): job], null)
		Workspace sandbox = new Workspace("sandbox", Path.of("/sandbox"))
		service.openExecution(production)
		service.openExecution(sandbox)

		when:
		String token = block(service.getEnvironmentVariables(production, null)).RPA_CALLBACK_TOKEN
		String other = block(service.getEnvironmentVariables(sandbox, null)).RPA_CALLBACK_TOKEN

		then:
		token ==~ /[A-Za-z0-9_-]{43}/
		token != other
		block(service.getEnvironmentVariables(production, null)).RPA_CALLBACK_TOKEN == token
		service.authorizesWorkspace(token, production.id())
		!service.authorizesWorkspace(token, sandbox.id())
		!service.authorizesWorkspace(other, production.id())
		!service.authorizesWorkspace(null, production.id())
		service.authorizesJob(token, 123L)
		!service.authorizesJob(token, 124L)
		!service.authorizesJob(other, 123L)
		service.authorizesJob(other, -1L)
		!service.authorizesJob(token, -1L)

		when:
		service.afterRobotExecution(production)

		then:
		!service.authorizesWorkspace(token, production.id())
		!service.authorizesJob(token, 123L)
		service.authorizesWorkspace(other, sandbox.id())

		when:
		block(service.getEnvironmentVariables(production, null))

		then:
		thrown(IllegalStateException)
		!service.authorizesWorkspace(token, production.id())
	}

	void "An empty administrative token never authenticates"() {
		expect:
		!CallbackTokenService.tokensMatch("", "")
		!CallbackTokenService.tokensMatch("", null)
		!CallbackTokenService.tokensMatch(null, "value")
		!CallbackTokenService.tokensMatch("expected", "different")
	}

	void "Invalid token configuration fails without including the credential in the error"() {
		when:
		new ApiSecurityProperties("too-short-sensitive-value", [])

		then:
		def error = thrown(IllegalArgumentException)
		!error.message.contains("too-short-sensitive-value")
	}

	void "API configuration string representation redacts the token"() {
		given:
		String token = "sensitive-api-token-of-at-least-32-characters"

		expect:
		!new ApiSecurityProperties(token, null).toString().contains(token)
		new ApiSecurityProperties("", null).allowedHosts().isEmpty()
	}
}
