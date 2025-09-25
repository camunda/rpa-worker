package io.camunda.rpa.worker.zeebe;

import io.camunda.rpa.worker.util.HttpHeaderUtils;
import io.camunda.zeebe.spring.client.actuator.ZeebeClientHealthIndicator;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.common.ApiProperties;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
@RequiredArgsConstructor
class ZeebeConfiguration {

	private final ZeebeProperties zeebeProperties;
	private final ObjectProvider<CamundaClientProperties> camundaClientProperties;
	private final ZeebeAuthProperties zeebeAuthProperties;
	
	@Bean
	public BeanPostProcessor zeebeHealthCheckBeanPostProcessor(ZeebeClientStatus zeebeClientStatus) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
				if ( ! zeebeClientStatus.isZeebeClientEnabled()
						&& bean instanceof ZeebeClientHealthIndicator)
					return new ZeebeClientHealthIndicator(null) {
						@Override
						protected void doHealthCheck(Health.Builder builder) {}
					};

				return bean;
			}
		};
	}
	
	@Bean
	public ZeebeClientStatus zeebeClientStatus() {
		boolean zeebeEnabled = camundaClientProperties.stream().findFirst()
				.map(CamundaClientProperties::getZeebe)
				.map(ApiProperties::getEnabled)
				.orElse(false);

		boolean clientModeConfigured = camundaClientProperties.stream().findFirst()
				.map(CamundaClientProperties::getMode)
				.isPresent();

		return () -> (zeebeEnabled && clientModeConfigured) || zeebeProperties.authMethod() != ZeebeProperties.AuthMethod.TOKEN;
	}
	
	@Bean
	public ClientInterceptor grpcAuthenticatingClientInterceptor(ZeebeProperties zeebeProperties) {
		Metadata m = new Metadata();
		if(zeebeProperties.authMethod() == ZeebeProperties.AuthMethod.BASIC)
			m.put(Metadata.Key.of(HttpHeaders.AUTHORIZATION, Metadata.ASCII_STRING_MARSHALLER), 
					HttpHeaderUtils.basicAuth(zeebeAuthProperties.clientId(), zeebeAuthProperties.clientSecret()));
		return MetadataUtils.newAttachHeadersInterceptor(m);
	}
}
