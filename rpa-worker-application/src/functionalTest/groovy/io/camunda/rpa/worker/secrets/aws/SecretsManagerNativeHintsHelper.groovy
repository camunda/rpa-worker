package io.camunda.rpa.worker.secrets.aws

import io.camunda.rpa.worker.AbstractFunctionalSpec
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException
import spock.lang.IgnoreIf

@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
class SecretsManagerNativeHintsHelper extends AbstractFunctionalSpec {
	
	void "Happy path"() {
		when:
		SecretsManagerProperties SecretsManagerProperties = new SecretsManagerProperties(["test/secrets"])
		Map<String, Object> secrets = block(new SecretsManagerBackend(SecretsManagerProperties, objectMapper, {
			SecretsManagerAsyncClient.builder()
					.region(Region.EU_WEST_2)
					.credentialsProvider(ProfileCredentialsProvider.builder()
							.profileName("secretstest")
							.build())
					.build()
		}).getSecrets())

		then:
		! secrets.isEmpty()
	}

	void "No secret"() {
		when:
		SecretsManagerProperties SecretsManagerProperties = new SecretsManagerProperties(["test/fakesecrets"])
		Map<String, Object> secrets = block(new SecretsManagerBackend(SecretsManagerProperties, objectMapper, {
			SecretsManagerAsyncClient.builder()
					.region(Region.EU_WEST_2)
					.credentialsProvider(ProfileCredentialsProvider.builder()
							.profileName("secretstest")
							.build())
					.build()
		}).getSecrets())

		then:
		thrown(ResourceNotFoundException)
	}
	
	void "Bad credentials 1"() {
		when:
		SecretsManagerProperties SecretsManagerProperties = new SecretsManagerProperties(["test/secrets"])
		Map<String, Object> secrets = block(new SecretsManagerBackend(SecretsManagerProperties, objectMapper, {
			SecretsManagerAsyncClient.builder()
					.region(Region.EU_WEST_2)
					.credentialsProvider(ProfileCredentialsProvider.builder()
							.profileName("fakeprofile")
							.build())
					.build()
		}).getSecrets())

		then:
		thrown(SdkClientException)
	}

	void "Bad credentials 2"() {
		when:
		SecretsManagerProperties SecretsManagerProperties = new SecretsManagerProperties(["test/secrets"])
		Map<String, Object> secrets = block(new SecretsManagerBackend(SecretsManagerProperties, objectMapper, {
			SecretsManagerAsyncClient.builder()
					.region(Region.EU_WEST_2)
					.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.builder()
							.accessKeyId("FAKE_ACCESS_KEY")
							.secretAccessKey("FAKE_SECRET_KEY")
							.build()))
			.build()
		}).getSecrets())

		then:
		thrown(SecretsManagerException)
	}

	void "Unconfigured"() {
		when:
		SecretsManagerProperties SecretsManagerProperties = new SecretsManagerProperties(["test/secrets"])
		Map<String, Object> secrets = block(new SecretsManagerBackend(SecretsManagerProperties, objectMapper, {
			SecretsManagerAsyncClient.builder()
					.region(Region.EU_WEST_2)
					.build()
		}).getSecrets())

		then:
		! secrets.isEmpty()
	}
}
