package io.camunda.rpa.worker.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.ApplicationEvent;

class ZeebeReadyEvent extends ApplicationEvent {
	public ZeebeReadyEvent(ZeebeClient zeebeClient) {
		super(zeebeClient);
	}
}
