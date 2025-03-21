package io.camunda.rpa.worker.secrets.aws;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.internal.LazyAwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.internal.SystemSettingsCredentialsProvider;

import java.util.stream.Stream;

@Configuration
@ImportRuntimeHints(SecretsManagerHints.class)
class SecretsManagerHints implements RuntimeHintsRegistrar {
	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		Stream.of(
						AnonymousCredentialsProvider.class,
						AwsCredentialsProviderChain.class,
						ContainerCredentialsProvider.class,
						DefaultCredentialsProvider.class,
						EnvironmentVariableCredentialsProvider.class,
						InstanceProfileCredentialsProvider.class,
						ProcessCredentialsProvider.class,
						ProfileCredentialsProvider.class,
						StaticCredentialsProvider.class,
						SystemPropertyCredentialsProvider.class,
						WebIdentityTokenFileCredentialsProvider.class,
						LazyAwsCredentialsProvider.class,
						SystemSettingsCredentialsProvider.class)
				.forEach(klass -> hints
						.reflection()
						.registerType(
								klass,
								MemberCategory.values()));
	}
}
