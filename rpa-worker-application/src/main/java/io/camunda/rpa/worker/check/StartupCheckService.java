package io.camunda.rpa.worker.check;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.function.BiConsumer;

@Service
@Slf4j
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class StartupCheckService implements ApplicationListener<ApplicationReadyEvent> {
	
	private final ObjectProvider<StartupCheck<?>> startupChecks;
	private final ApplicationEventPublisher eventPublisher;
	private final BiConsumer<ApplicationContext, ExitCodeGenerator> exiter;

	@Autowired
	public StartupCheckService(ObjectProvider<StartupCheck<?>> startupChecks, ApplicationEventPublisher eventPublisher) {
		this(startupChecks, eventPublisher, 
				(ctx, exit) -> System.exit(SpringApplication.exit(ctx, exit)));
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		Flux.fromStream(startupChecks.orderedStream())
				
				.doOnNext(check -> log.atInfo()
						.kv("check", check.getClass().getSimpleName())
						.log("Performing startup check"))
				
				.concatMap(check -> check.check()
						.publishOn(Schedulers.boundedElastic())
						.doOnError(thrown -> log.atDebug().setCause(thrown).log("Startup check failed"))
						.doOnError(_ -> exiter.accept(
								event.getApplicationContext(), check::getExitCodeForFailure)))
				
				.doOnNext(eventPublisher::publishEvent)
				.then()
				.onErrorComplete()
				.block();
	}

	@Override
	public boolean supportsAsyncExecution() {
		return false;
	}
}
