package io.camunda.rpa.worker.secrets.k8s

import io.camunda.rpa.worker.PublisherUtils
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretList
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.MixedOperation
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation
import io.fabric8.kubernetes.client.dsl.Resource
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Function

class K8sBackendSpec extends Specification implements PublisherUtils {

	K8sProperties k8sProperties = new K8sProperties(["secret1@namespace", "secret2@namespace", "missing-secret@namespace"])
	NonNamespaceOperation<Secret, SecretList, Resource<Secret>> secretsClientInNamespace = Stub() {
		withName("secret1") >> Stub(Resource<Secret>) {
			get() >> Stub(Secret) {
				getData() >> [foo: 'bar'.bytes.encodeBase64().toString()]
			}
		}
		withName("secret2") >> Stub(Resource<Secret>) {
			get() >> Stub(Secret) {
				getData() >> [baz: 'bat'.bytes.encodeBase64().toString()]
			}
		}
		withName("missing-secret") >> Stub(Resource<Secret>) {
			get() >> null
		}
	}
	MixedOperation<Secret, SecretList, Resource<Secret>> secretsClient = Stub() {
		inNamespace("namespace") >> { secretsClientInNamespace }
	}
	KubernetesClient kubernetesClient = Stub() {
		secrets() >> { secretsClient }
	}
	Function<ApplicationContext, KubernetesClient> kubernetesClientFactory = { kubernetesClient }

	@Subject
	K8sBackend backend = new K8sBackend(k8sProperties, null, kubernetesClientFactory)


	void "Fetches secrets from client and returns merged map"() {
		when:
		Map<String, Object> secrets = block backend.getSecrets()

		then:
		secrets == new LinkedHashMap().tap {
			it['foo'] = "bar"
			it['baz'] = "bat"
		}
	}
}
