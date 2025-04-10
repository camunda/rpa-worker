package io.camunda.rpa.worker.robot;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RobotExecutionStrategyFactoryBean implements FactoryBean<RobotExecutionStrategy> {
	
//	PythonProp
	
	@Override
	public Class<?> getObjectType() {
		return RobotExecutionStrategy.class;
	}

	@Override
	public RobotExecutionStrategy getObject() throws Exception {
		return null;
	}
}
