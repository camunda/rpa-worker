package io.camunda.rpa.worker;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusProperties;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusPushGatewayManager;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RpaWorkerApplication {
	
	public static final int EXIT_NO_ZEEBE_CONNECTION = 199;
	public static final int EXIT_NO_PYTHON = 198;
	public static final int EXIT_NO_ROBOT = 197;

	public static void main(String[] args) {
		System.setProperty("spring.config.name", "application,rpa-worker");
		SpringApplication.run(RpaWorkerApplication.class, args);
	}

	@Bean
	public WebClient webClient(WebClient.Builder builder) {
		return builder
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().followRedirect(true)))
				.build();
	}
	
	@Bean
	BeanPostProcessor pushGatewayBeanPostProcessor(PrometheusProperties prometheusProperties) {

		if(prometheusProperties.getPushgateway().getEnabled()) 
			LoggingSystem.get(PrometheusPushGatewayManager.class.getClassLoader())
					.setLogLevel(PrometheusPushGatewayManager.class.getName(), LogLevel.WARN);

		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if ( ! prometheusProperties.getPushgateway().getEnabled()
						&& bean instanceof PrometheusPushGatewayManager mgr)
					mgr.shutdown();

				return bean;
			}
		};
	}
}
