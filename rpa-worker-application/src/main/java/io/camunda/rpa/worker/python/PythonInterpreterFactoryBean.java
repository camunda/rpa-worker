package io.camunda.rpa.worker.python;

import io.camunda.rpa.worker.robot.ResolvedRobotExecutionStrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PythonInterpreterFactoryBean implements FactoryBean<PythonInterpreter> {
	
	private final ResolvedRobotExecutionStrategyType strategyType;
	private final PythonSetupService pythonSetupService;

	@Override
	public Class<?> getObjectType() {
		return PythonInterpreter.class;
	}

	@Override
	public PythonInterpreter getObject() throws Exception {
		if(strategyType.getType() == PythonRuntimeProperties.PythonRuntimeEnvironment.Static)
			return null;
		
		return pythonSetupService.getPythonInterpreter().block();
	}
}
