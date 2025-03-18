package io.camunda.rpa.worker.util;

import io.camunda.rpa.worker.RpaWorkerApplication;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationRestarter implements ApplicationContextAware {
	
	private ConfigurableApplicationContext applicationContext;

	public void restart() {
		ApplicationArguments args = applicationContext.getBean(ApplicationArguments.class);

		Thread thread = new Thread(() -> {
			applicationContext.close();
			while (true) {
				try {
					RpaWorkerApplication.startApplication(args.getSourceArgs());
					break;
				}
				catch (IllegalStateException ignored) {
					Try.run(() -> Thread.sleep(1_000));
				}
			}
		});

		thread.setDaemon(false);
		thread.setName("restarter");
		thread.start();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}
}
