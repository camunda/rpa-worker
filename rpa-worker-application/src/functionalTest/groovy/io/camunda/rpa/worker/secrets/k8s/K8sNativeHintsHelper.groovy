package io.camunda.rpa.worker.secrets.k8s

import io.camunda.rpa.worker.AbstractFunctionalSpec
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import spock.lang.IgnoreIf

import java.nio.file.Paths

@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
class K8sNativeHintsHelper extends AbstractFunctionalSpec {

	static Closure<KubernetesClient> realConfigClientFactory = {
		new KubernetesClientBuilder()
				.withConfig(Config.fromKubeconfig(Paths.get("/data/k3s/etc/rancher/k3s/k3s.yaml").toFile()))
				.build()
	}

	@TestPropertySource(properties = [
			"camunda.rpa.secrets.backend=k8s",
	])
	@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
	static class HappyPath extends AbstractFunctionalSpec {

		@Autowired
		ApplicationContext applicationContext

		void "Happy path"() {
			when:
			K8sProperties k8sProperties = new K8sProperties(["secrets-test@secrets-test"])
			Map<String, Object> secrets = new K8sBackend(
					k8sProperties, applicationContext, realConfigClientFactory).getSecrets().block()

			then:
			! secrets.isEmpty()
		}
	}

	@TestPropertySource(properties = [
			"camunda.rpa.secrets.backend=k8s",
	])
	@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
	static class NoSecret extends AbstractFunctionalSpec {

		@Autowired
		ApplicationContext applicationContext

		void "No Secret"() {
			when:
			K8sProperties k8sProperties = new K8sProperties(["fake-secret@secrets-test"])
			Map<String, Object> secrets = block new K8sBackend(
					k8sProperties, applicationContext, realConfigClientFactory).getSecrets()

			then:
			secrets.isEmpty()
		}
	}

	@TestPropertySource(properties = [
			"camunda.rpa.secrets.backend=k8s",
	])
	@IgnoreIf({ ! System.getenv()['NATIVE_HINTS_HELPERS_ENABLED'] })
	static class BadCredentials extends AbstractFunctionalSpec {

		@Autowired
		ApplicationContext applicationContext

		void "Bad Credentials"() {
			when:
			K8sProperties k8sProperties = new K8sProperties(["fake-secret@secrets-test"])
			Map<String, Object> secrets = new K8sBackend(
					k8sProperties, applicationContext).getSecrets().block()

			then:
			thrown(KubernetesClientException)
		}
	}
}
