package io.camunda.rpa.worker.check

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.BiConsumer
import java.util.stream.Stream

class StartupCheckServiceSpec extends Specification {
	
	ApplicationEvent event = Stub()
	
	StartupCheck passes = Mock() {
		check() >> Mono.just(event)
	}
	
	StartupCheck fails = Stub() {
		check() >> Mono.error(new RuntimeException("Bang!"))
		getExitCodeForFailure() >> 127
	}

	ObjectProvider<StartupCheck> checks = Stub()
	ApplicationEventPublisher eventPublisher = Mock()
	BiConsumer<ApplicationContext, ExitCodeGenerator> exiter = Mock()
	
	@Subject
	StartupCheckService service = new StartupCheckService(checks, eventPublisher, exiter)

	ConfigurableApplicationContext applicationContext = Stub(ConfigurableApplicationContext)
	ApplicationReadyEvent are = new ApplicationReadyEvent(Stub(SpringApplication), null, applicationContext, null)

	void "Publishes ready event when check succeeds"() {
		given:
		checks.orderedStream() >> { Stream.of(passes) }
		
		when:
		service.onApplicationEvent(are)
		
		then:
		1 * eventPublisher.publishEvent(event)
		0 * exiter._(*_)
	}
	
	void "Exits application on failed check"() {
		given:
		checks.orderedStream() >> { Stream.of(fails) }

		when:
		service.onApplicationEvent(are)

		then:
		0 * eventPublisher.publishEvent(_)
		1 * exiter.accept(applicationContext, { ExitCodeGenerator ecg -> ecg.exitCode == 127 })
	}
	
	void "Executes checks sequentially"() {
		given:
		checks.orderedStream() >> { Stream.of(fails, passes) }

		when:
		service.onApplicationEvent(are)

		then: "No calls to the second check, because we have already failed"
		0 * passes.check()
	}
}
