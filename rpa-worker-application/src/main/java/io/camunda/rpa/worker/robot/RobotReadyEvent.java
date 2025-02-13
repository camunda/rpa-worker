package io.camunda.rpa.worker.robot;

import org.springframework.context.ApplicationEvent;

class RobotReadyEvent extends ApplicationEvent {
	public RobotReadyEvent() {
		super(new Object());
	}
}
