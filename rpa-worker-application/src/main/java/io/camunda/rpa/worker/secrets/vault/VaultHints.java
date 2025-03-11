package io.camunda.rpa.worker.secrets.vault;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AuthenticationStepsExecutor;
import org.springframework.vault.authentication.AwsEc2Authentication;
import org.springframework.vault.authentication.AwsIamAuthentication;
import org.springframework.vault.authentication.AzureMsiAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.GcpComputeAuthentication;
import org.springframework.vault.authentication.GcpIamCredentialsAuthentication;
import org.springframework.vault.authentication.JwtAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.LoginTokenAdapter;
import org.springframework.vault.authentication.PcfAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.authentication.UsernamePasswordAuthentication;

import java.util.stream.Stream;

@Configuration
@ImportRuntimeHints(VaultHints.class)
class VaultHints implements RuntimeHintsRegistrar {
	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		Stream.of(
						AppRoleAuthentication.class,
						AuthenticationStepsExecutor.class,
						AwsEc2Authentication.class,
						AwsIamAuthentication.class,
						AzureMsiAuthentication.class,
						ClientCertificateAuthentication.class,
						CubbyholeAuthentication.class,
						GcpComputeAuthentication.class,
						GcpIamCredentialsAuthentication.class,
						JwtAuthentication.class,
						KubernetesAuthentication.class,
						LoginTokenAdapter.class,
						PcfAuthentication.class,
						TokenAuthentication.class,
						UsernamePasswordAuthentication.class)
				.forEach(klass -> hints
						.reflection()
						.registerType(
								klass,
								MemberCategory.values()));
	}
}
