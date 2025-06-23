package io.camunda.rpa.worker.zeebe

import com.fasterxml.jackson.databind.ObjectMapper
import io.camunda.rpa.worker.PublisherUtils
import io.camunda.rpa.worker.io.IO
import io.camunda.rpa.worker.script.RobotScript
import org.intellij.lang.annotations.Language
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Paths
import java.util.function.Supplier

class ZeebeResourceScriptRepositorySpec extends Specification implements PublisherUtils {

	ResourceClient resourceClient = Mock()
	IO io = Stub() {
		supply(_) >> { Supplier s -> Mono.just(s.get()) }
	}
	ObjectMapper objectMapper = new ObjectMapper()

	@Language("json")
	String anRpaResource = """\
{
	"id": "the-resource-key",
	"name": "the-resource-name",
	"executionPlatform": "",
	"executionPlatformVersion": "",
	"script": "the-script-body"
}
"""
	
	@Subject
	ZeebeResourceScriptRepository repository = 
			new ZeebeResourceScriptRepository(resourceClient, io, objectMapper)
	
	void "Fetches script resource from Zeebe"() {
		given:
		Flux<DataBuffer> rpaResourceData = DataBufferUtils.readInputStream(
				() -> new ByteArrayInputStream(anRpaResource.bytes), 
				DefaultDataBufferFactory.sharedInstance, 
				8192)
		
		and:
		io.write(_, _) >> Mono.empty()
		io.withReader(_, _) >> { __, fn -> fn.apply(new StringReader(anRpaResource)) }
		
		and:
		resourceClient.getRpaResource("the-resource-key") >> rpaResourceData
				
		when:
		RobotScript script = block repository.getById("the-resource-key")
		
		then:
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
	}

	void "Caches script and uses cache when available"() {
		given:
		Flux<DataBuffer> rpaResourceData = DataBufferUtils.readInputStream(
				() -> new ByteArrayInputStream(anRpaResource.bytes),
				DefaultDataBufferFactory.sharedInstance,
				8192)

		and:
		io.write(_, _) >> Mono.empty()
		io.withReader(_, _) >> { __, fn -> fn.apply(new StringReader(anRpaResource)) }

		when:
		RobotScript script = block repository.getById("the-resource-key")

		then:
		1 * resourceClient.getRpaResource("the-resource-key") >> rpaResourceData
		
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
		
		when:
		RobotScript script2 = block repository.getById("the-resource-key")

		then:
		0 * resourceClient.getRpaResource(_)

		script2.id() == "the-resource-key"
		script2.body() == "the-script-body"
	}

	void "Fetches script resource from Zeebe - includes additional files"() {
		given:
		@Language("json")
		String rpaResourceWithFiles = """\
{
	"id": "the-resource-key",
	"name": "the-resource-name",
	"executionPlatform": "",
	"executionPlatformVersion": "",
	"script": "the-script-body",
	"files": {
		"one.resource": "H4sIAAAAAAAAA8vPS9UrSi3OLy1KTlVIzs8rSc0rAQDo66f1FAAAAA==",
		"two/three.resource": "H4sIAAAAAAAAAyvJKEpN1StKLc4vLUpOVUjOzytJzSsBADv+Z18WAAAA"
	}
}
"""

		Flux<DataBuffer> rpaResourceData = DataBufferUtils.readInputStream(
				() -> new ByteArrayInputStream(rpaResourceWithFiles.bytes),
				DefaultDataBufferFactory.sharedInstance,
				8192)
		
		and:
		resourceClient.getRpaResource("the-resource-key") >> rpaResourceData

		and:
		io.write(_, _) >> Mono.empty()
		io.withReader(_, _) >> { __, fn -> fn.apply(new StringReader(rpaResourceWithFiles)) }

		when:
		RobotScript script = block repository.getById("the-resource-key")

		then:
		script.id() == "the-resource-key"
		script.body() == "the-script-body"
		script.files() == [
				(Paths.get("one.resource"))    : "one.resource content".bytes,
				(Paths.get("two/three.resource")): "three.resource content".bytes
		]
	}
}
