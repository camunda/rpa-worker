package io.camunda.rpa.worker.python;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PythonInterpreterFactoryBean implements FactoryBean<PythonInterpreter> {
	
	private final PythonRuntimeProperties pythonRuntimeProperties;
	private final PythonSetupService pythonSetupService;

	@Override
	public Class<?> getObjectType() {
		return PythonInterpreter.class;
	}

	@Override
	public PythonInterpreter getObject() throws Exception {
		if(pythonRuntimeProperties.type() == PythonRuntimeProperties.PythonRuntimeEnvironment.Static)
			return null;
		
		return pythonSetupService.getPythonInterpreter().block();
	}
}
