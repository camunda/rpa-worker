package io.camunda.rpa.worker.zeebe

import io.camunda.client.spring.properties.CamundaClientAuthProperties
import io.camunda.client.spring.properties.CamundaClientProperties
import io.camunda.rpa.worker.AbstractFunctionalSpec
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource

import java.util.concurrent.TimeUnit

@TestPropertySource(properties = ["camunda.client.auth.method=none", "camunda.rpa.zeebe.auth-method=none"])
class OidcAuthenticationIntegrationSpec extends AbstractFunctionalSpec {

	void "Uses issuer URL to fetch configuration and then token URL"() {
		given:
		zeebeAuth.enqueue(new MockResponse().tap {
			setResponseCode(200)
			setBody(OIDC_CONFIG_RESPONSE)
			setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		})

		and:
		ZeebeProperties zeebeProperties = new ZeebeProperties(null, null, 1, ZeebeProperties.AuthMethod.TOKEN)
		CamundaClientProperties camundaProperties = Stub() {
			getAuth() >> Stub(CamundaClientAuthProperties) {
				getMethod() >> CamundaClientAuthProperties.AuthMethod.oidc
				getTokenUrl() >> null
				getIssuerUrl() >> "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_AUTH_PORT}/auth/realms/camunda-platform".toURI()
			}
		}

		when:
		URI r = OidcConfigurationHelper.getTokenUrl(zeebeProperties, camundaProperties, webClient)
		RecordedRequest request = zeebeAuth.takeRequest(1, TimeUnit.SECONDS)

		then:
		request
		request.path == "/auth/realms/camunda-platform/.well-known/openid-configuration"
		
		and:
		r == "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token".toURI()
	}

	void "Uses explicit token URL as token URL"() {
		given:
		zeebeAuth.enqueue(new MockResponse().tap {
			setResponseCode(200)
			setBody(OIDC_CONFIG_RESPONSE)
			setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		})

		and:
		ZeebeProperties zeebeProperties = new ZeebeProperties(null, null, 1, ZeebeProperties.AuthMethod.TOKEN)
		CamundaClientProperties camundaProperties = Stub() {
			getAuth() >> Stub(CamundaClientAuthProperties) {
				getMethod() >> CamundaClientAuthProperties.AuthMethod.oidc
				getIssuerUrl() >> null
				getTokenUrl() >> "http://localhost:${AbstractFunctionalSpec.ZEEBE_MOCK_AUTH_PORT}/auth/realms/camunda-platform/protocol/openid-connect/token".toURI()
			}
		}

		when:
		URI r = OidcConfigurationHelper.getTokenUrl(zeebeProperties, camundaProperties, webClient)
		RecordedRequest request = zeebeAuth.takeRequest(1, TimeUnit.SECONDS)

		then:
		! request
		
		and:
		r == "http://localhost:18180/auth/realms/camunda-platform/protocol/openid-connect/token".toURI()
	}

	private static final String OIDC_CONFIG_RESPONSE = """\
{
  "issuer": "http://localhost:18080/auth/realms/camunda-platform",
  "authorization_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/auth",
  "token_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token",
  "introspection_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token/introspect",
  "userinfo_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/userinfo",
  "end_session_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/logout",
  "frontchannel_logout_session_supported": true,
  "frontchannel_logout_supported": true,
  "jwks_uri": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/certs",
  "check_session_iframe": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/login-status-iframe.html",
  "grant_types_supported": [
    "authorization_code",
    "client_credentials",
    "implicit",
    "password",
    "refresh_token",
    "urn:ietf:params:oauth:grant-type:device_code",
    "urn:ietf:params:oauth:grant-type:token-exchange",
    "urn:ietf:params:oauth:grant-type:uma-ticket",
    "urn:openid:params:grant-type:ciba"
  ],
  "acr_values_supported": [
    "0",
    "1"
  ],
  "response_types_supported": [
    "code",
    "none",
    "id_token",
    "token",
    "id_token token",
    "code id_token",
    "code token",
    "code id_token token"
  ],
  "subject_types_supported": [
    "public",
    "pairwise"
  ],
  "prompt_values_supported": [
    "none",
    "login",
    "consent"
  ],
  "id_token_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512"
  ],
  "id_token_encryption_alg_values_supported": [
    "ECDH-ES+A256KW",
    "ECDH-ES+A192KW",
    "ECDH-ES+A128KW",
    "RSA-OAEP",
    "RSA-OAEP-256",
    "RSA1_5",
    "ECDH-ES"
  ],
  "id_token_encryption_enc_values_supported": [
    "A256GCM",
    "A192GCM",
    "A128GCM",
    "A128CBC-HS256",
    "A192CBC-HS384",
    "A256CBC-HS512"
  ],
  "userinfo_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512",
    "none"
  ],
  "userinfo_encryption_alg_values_supported": [
    "ECDH-ES+A256KW",
    "ECDH-ES+A192KW",
    "ECDH-ES+A128KW",
    "RSA-OAEP",
    "RSA-OAEP-256",
    "RSA1_5",
    "ECDH-ES"
  ],
  "userinfo_encryption_enc_values_supported": [
    "A256GCM",
    "A192GCM",
    "A128GCM",
    "A128CBC-HS256",
    "A192CBC-HS384",
    "A256CBC-HS512"
  ],
  "request_object_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512",
    "none"
  ],
  "request_object_encryption_alg_values_supported": [
    "ECDH-ES+A256KW",
    "ECDH-ES+A192KW",
    "ECDH-ES+A128KW",
    "RSA-OAEP",
    "RSA-OAEP-256",
    "RSA1_5",
    "ECDH-ES"
  ],
  "request_object_encryption_enc_values_supported": [
    "A256GCM",
    "A192GCM",
    "A128GCM",
    "A128CBC-HS256",
    "A192CBC-HS384",
    "A256CBC-HS512"
  ],
  "response_modes_supported": [
    "query",
    "fragment",
    "form_post",
    "query.jwt",
    "fragment.jwt",
    "form_post.jwt",
    "jwt"
  ],
  "registration_endpoint": "http://localhost:18080/auth/realms/camunda-platform/clients-registrations/openid-connect",
  "token_endpoint_auth_methods_supported": [
    "private_key_jwt",
    "client_secret_basic",
    "client_secret_post",
    "tls_client_auth",
    "client_secret_jwt"
  ],
  "token_endpoint_auth_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512"
  ],
  "introspection_endpoint_auth_methods_supported": [
    "private_key_jwt",
    "client_secret_basic",
    "client_secret_post",
    "tls_client_auth",
    "client_secret_jwt"
  ],
  "introspection_endpoint_auth_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512"
  ],
  "authorization_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512"
  ],
  "authorization_encryption_alg_values_supported": [
    "ECDH-ES+A256KW",
    "ECDH-ES+A192KW",
    "ECDH-ES+A128KW",
    "RSA-OAEP",
    "RSA-OAEP-256",
    "RSA1_5",
    "ECDH-ES"
  ],
  "authorization_encryption_enc_values_supported": [
    "A256GCM",
    "A192GCM",
    "A128GCM",
    "A128CBC-HS256",
    "A192CBC-HS384",
    "A256CBC-HS512"
  ],
  "claims_supported": [
    "aud",
    "sub",
    "iss",
    "auth_time",
    "name",
    "given_name",
    "family_name",
    "preferred_username",
    "email",
    "acr"
  ],
  "claim_types_supported": [
    "normal"
  ],
  "claims_parameter_supported": true,
  "scopes_supported": [
    "openid",
    "address",
    "roles",
    "phone",
    "profile",
    "service_account",
    "acr",
    "microprofile-jwt",
    "camunda-identity",
    "email",
    "offline_access",
    "web-origins",
    "basic"
  ],
  "request_parameter_supported": true,
  "request_uri_parameter_supported": true,
  "require_request_uri_registration": true,
  "code_challenge_methods_supported": [
    "plain",
    "S256"
  ],
  "tls_client_certificate_bound_access_tokens": true,
  "revocation_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/revoke",
  "revocation_endpoint_auth_methods_supported": [
    "private_key_jwt",
    "client_secret_basic",
    "client_secret_post",
    "tls_client_auth",
    "client_secret_jwt"
  ],
  "revocation_endpoint_auth_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "HS256",
    "HS512",
    "ES256",
    "RS256",
    "HS384",
    "ES512",
    "PS256",
    "PS512",
    "RS512"
  ],
  "backchannel_logout_supported": true,
  "backchannel_logout_session_supported": true,
  "device_authorization_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/auth/device",
  "backchannel_token_delivery_modes_supported": [
    "poll",
    "ping"
  ],
  "backchannel_authentication_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/ext/ciba/auth",
  "backchannel_authentication_request_signing_alg_values_supported": [
    "PS384",
    "RS384",
    "EdDSA",
    "ES384",
    "ES256",
    "RS256",
    "ES512",
    "PS256",
    "PS512",
    "RS512"
  ],
  "require_pushed_authorization_requests": false,
  "pushed_authorization_request_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/ext/par/request",
  "mtls_endpoint_aliases": {
    "token_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token",
    "revocation_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/revoke",
    "introspection_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token/introspect",
    "device_authorization_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/auth/device",
    "registration_endpoint": "http://localhost:18080/auth/realms/camunda-platform/clients-registrations/openid-connect",
    "userinfo_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/userinfo",
    "pushed_authorization_request_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/ext/par/request",
    "backchannel_authentication_endpoint": "http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/ext/ciba/auth"
  },
  "authorization_response_iss_parameter_supported": true
}
"""
}