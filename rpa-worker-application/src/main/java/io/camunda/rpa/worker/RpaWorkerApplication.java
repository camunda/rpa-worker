package io.camunda.rpa.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusProperties;
import org.springframework.boot.micrometer.metrics.export.prometheus.PrometheusPushGatewayManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@SpringBootApplication
@ConfigurationPropertiesScan
@Slf4j
public class RpaWorkerApplication {
	
	public static final int EXIT_NO_ZEEBE_CONNECTION = 199;
	public static final int EXIT_NO_PYTHON = 198;
	public static final int EXIT_NO_ROBOT = 197;

	public static void main(String[] args) {
		System.setProperty("slf4j.internal.verbosity", "WARN");
		System.setProperty("spring.config.name", "application,rpa-worker");
		startApplication(args);
	}

	public static void startApplication(String[] args) {
		SpringApplication springApplication = new SpringApplication(RpaWorkerApplication.class);
		springApplication.setMainApplicationClass(RpaWorkerApplication.class);
		springApplication.run(args);
	}

	@Bean
	public WebClient webClient(WebClient.Builder builder) {
		return builder
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
				.build();
	}
	
	@Bean
	BeanPostProcessor pushGatewayBeanPostProcessor(PrometheusProperties prometheusProperties) {

		if(prometheusProperties.getPushgateway().isEnabled()) 
			LoggingSystem.get(PrometheusPushGatewayManager.class.getClassLoader())
					.setLogLevel(PrometheusPushGatewayManager.class.getName(), LogLevel.WARN);

		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if ( ! prometheusProperties.getPushgateway().isEnabled()
						&& bean instanceof PrometheusPushGatewayManager mgr)
					mgr.shutdown();

				return bean;
			}
		};
	}
}
