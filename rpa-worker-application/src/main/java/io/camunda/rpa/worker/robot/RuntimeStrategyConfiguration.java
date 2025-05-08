package io.camunda.rpa.worker.robot;

import io.camunda.rpa.worker.io.IO;
import io.camunda.rpa.worker.pexec.ProcessService;
import io.camunda.rpa.worker.python.PythonInterpreter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RuntimeStrategyConfiguration {
	
	@Bean
	public RobotExecutionStrategy runtimeStrategy(
			ResolvedRobotExecutionStrategyType strategyType,
			ProcessService processService,
			PythonInterpreter pythonInterpreter, 
			IO io) {
		
		return switch(strategyType.getType()) {
			case Python -> new PythonRobotExecutionStrategy(processService, pythonInterpreter);
			case Static -> new StaticRobotExecutionStrategy(processService, io);
			default -> throw new IllegalStateException("Unexpected value: " + strategyType.getType());
		};
	}
	
}
